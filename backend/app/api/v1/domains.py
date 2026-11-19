from __future__ import annotations

import secrets
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import Integer, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import current_user
from app.db.models import Site, TrafficPoint, User
from app.db.session import get_db
from app.schemas import DomainIn
from app.services import buckets
from app.services.verify import verify_domain

router = APIRouter(prefix="/domains", tags=["domains"])


def _iso(value) -> str | None:
    return value.isoformat(sep=" ") if value else None


def _dto(site: Site) -> dict:
    return {
        "id": site.id,
        "domain": site.domain,
        "status": site.status,
        "verify_token": site.verify_token,
        "dns_name": f"_lanos-verify.{site.domain}",
        "dns_value": site.verify_token,
        "http_path": "/.well-known/lanos-verify.txt",
        "http_value": site.verify_token,
        "verified_at": _iso(site.verified_at),
        "created_at": _iso(site.created_at),
    }


async def _owned(db: AsyncSession, site_id: int, user: User) -> Site:
    site = await db.get(Site, site_id)
    if site is None or site.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "域名不存在")
    return site


@router.get("")
async def list_domains(
    user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> list[dict]:
    rows = (
        await db.execute(select(Site).where(Site.user_id == user.id).order_by(Site.id.desc()))
    ).scalars().all()
    return [_dto(s) for s in rows]


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_domain(
    payload: DomainIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    existing = (await db.execute(select(Site).where(Site.domain == payload.domain))).scalars().first()
    if existing is not None:
        if existing.user_id != user.id:
            raise HTTPException(status.HTTP_409_CONFLICT, "该域名已被其他账号添加")
        return _dto(existing)  # 幂等：重复添加直接返回已有记录

    site = Site(
        user_id=user.id,
        domain=payload.domain,
        status="pending",
        verify_token=f"lanos-verify-{secrets.token_hex(16)}",
    )
    db.add(site)
    await db.commit()
    await db.refresh(site)
    return _dto(site)


@router.post("/{site_id}/verify")
async def verify(
    site_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    site = await _owned(db, site_id, user)
    result = await verify_domain(site.domain, site.verify_token)
    if result["verified"]:
        site.status = "verified"
        site.verified_at = buckets.utcnow()
    else:
        site.status = "pending"
        site.verified_at = None
    await db.commit()
    return {
        "domain": site.domain,
        "status": site.status,
        "dns_ok": result["dns_ok"],
        "http_ok": result["http_ok"],
        "verified": result["verified"],
        "message": result["message"],
    }


@router.delete("/{site_id}")
async def delete_domain(
    site_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    site = await _owned(db, site_id, user)
    await db.delete(site)
    await db.commit()
    return {"message": "域名已删除"}


@router.get("/{site_id}/traffic")
async def traffic(
    site_id: int,
    range: str = Query(default="24h", alias="range"),
    user: User = Depends(current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    site = await _owned(db, site_id, user)
    span, bucket = buckets.resolve(range)
    since = buckets.utcnow() - timedelta(seconds=span + bucket)

    bucket_expr = func.floor(func.unix_timestamp(TrafficPoint.ts) / bucket).cast(Integer)
    stmt = (
        select(
            bucket_expr,
            func.sum(TrafficPoint.requests),
            func.sum(TrafficPoint.bytes),
            func.sum(TrafficPoint.blocked),
        )
        .where(TrafficPoint.site_id == site.id, TrafficPoint.ts >= since)
        .group_by(bucket_expr)
        .order_by(bucket_expr)
    )
    rows = (await db.execute(stmt)).all()
    points = buckets.fill(
        [(int(r[0] or 0), int(r[1] or 0), int(r[2] or 0), int(r[3] or 0)) for r in rows],
        range_key=range,
    )

    return {
        "range": range,
        "bucket": bucket,
        "points": points,
        "total_requests": sum(p["requests"] for p in points),
        "total_bytes": sum(p["bytes"] for p in points),
        "total_blocked": sum(p["blocked"] for p in points),
    }
