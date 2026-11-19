from __future__ import annotations

import asyncio

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import current_user
from app.db.models import CloudflareIntegration, Site, User, WafRule
from app.db.session import get_db
from app.services import cloudflare
from app.services.cf_sync import sync_once

router = APIRouter(prefix="/integrations/cloudflare", tags=["integrations"])


class CfIn(BaseModel):
    site_id: int
    zone_id: str
    token: str


async def _owned_site(db: AsyncSession, site_id: int, user: User) -> Site:
    site = await db.get(Site, site_id)
    if site is None or site.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "域名不存在")
    return site


def _dto(row: CloudflareIntegration | None) -> dict:
    if row is None:
        return {"connected": False}
    return {
        "connected": True,
        "zone_id": row.zone_id,
        "zone_name": row.zone_name,
        "enabled": row.enabled,
        "last_sync_at": row.last_sync_at.isoformat(sep=" ") if row.last_sync_at else None,
        "last_error": row.last_error,
    }


@router.get("/{site_id}")
async def get_cf(
    site_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    await _owned_site(db, site_id, user)
    row = (
        await db.execute(
            select(CloudflareIntegration).where(CloudflareIntegration.site_id == site_id)
        )
    ).scalars().first()
    return _dto(row)


@router.post("")
async def connect_cf(
    payload: CfIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    site = await _owned_site(db, payload.site_id, user)

    try:
        zone_name = await asyncio.to_thread(cloudflare.verify_zone, payload.token, payload.zone_id)
    except cloudflare.CloudflareError as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, f"Cloudflare 校验失败：{exc}") from exc

    row = (
        await db.execute(
            select(CloudflareIntegration).where(CloudflareIntegration.site_id == site.id)
        )
    ).scalars().first()
    if row is None:
        row = CloudflareIntegration(
            site_id=site.id, zone_id=payload.zone_id, token=payload.token, zone_name=zone_name
        )
        db.add(row)
    else:
        row.zone_id = payload.zone_id
        row.token = payload.token
        row.zone_name = zone_name
        row.enabled = True
        row.last_error = None
    await db.commit()
    await db.refresh(row)
    return _dto(row)


@router.post("/{site_id}/sync")
async def sync_now(
    site_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    await _owned_site(db, site_id, user)
    written = await sync_once()
    return {"message": "已同步", "points": written}


@router.delete("/{site_id}")
async def disconnect_cf(
    site_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    await _owned_site(db, site_id, user)
    row = (
        await db.execute(
            select(CloudflareIntegration).where(CloudflareIntegration.site_id == site_id)
        )
    ).scalars().first()
    if row is not None:
        await db.delete(row)
        await db.commit()
    return {"message": "已断开"}


class SecurityIn(BaseModel):
    level: str  # off | essentially_off | low | medium | high | under_attack


CF_RULE_LIMIT = 5  # 免费版自定义规则上限


def _cf_expression(rule: WafRule) -> str | None:
    pattern = rule.pattern.replace("\\", "\\\\").replace('"', '\\"')
    if rule.target == "path":
        return f'(http.request.uri.path matches "{pattern}")'
    if rule.target == "ua":
        return f'(http.user_agent matches "{pattern}")'
    if rule.target == "query":
        return f'(http.request.uri.query matches "{pattern}")'
    return None  # 请求正文 CF 自定义规则看不到


async def _cf_row(db: AsyncSession, site_id: int, user: User) -> CloudflareIntegration:
    await _owned_site(db, site_id, user)
    row = (
        await db.execute(
            select(CloudflareIntegration).where(CloudflareIntegration.site_id == site_id)
        )
    ).scalars().first()
    if row is None:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "这个域名还没接入 Cloudflare")
    return row


@router.post("/{site_id}/rules/push")
async def push_rules(
    site_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    row = await _cf_row(db, site_id, user)
    rules = (
        await db.execute(
            select(WafRule).where(WafRule.enabled.is_(True), WafRule.action == "block")
        )
    ).scalars().all()

    cf_rules: list[dict] = []
    skipped = 0
    for r in rules:
        expr = _cf_expression(r)
        if expr is None or len(cf_rules) >= CF_RULE_LIMIT:
            skipped += 1
            continue
        cf_rules.append(
            {
                "action": "block",
                "expression": expr,
                "description": f"LanOS: {r.name}"[:120],
                "enabled": True,
            }
        )

    try:
        result = await asyncio.to_thread(
            cloudflare.push_custom_rules, row.token, row.zone_id, cf_rules
        )
    except cloudflare.CloudflareError as exc:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"下发失败：{exc}") from exc
    return {"pushed": len(cf_rules), "skipped": skipped, "limit": CF_RULE_LIMIT, **result}


@router.get("/{site_id}/security")
async def get_security(
    site_id: int, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    row = await _cf_row(db, site_id, user)
    try:
        level = await asyncio.to_thread(cloudflare.get_security_level, row.token, row.zone_id)
    except cloudflare.CloudflareError as exc:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, str(exc)) from exc
    return {"level": level, "under_attack": level == "under_attack"}


@router.post("/{site_id}/security")
async def set_security(
    site_id: int,
    payload: SecurityIn,
    user: User = Depends(current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    row = await _cf_row(db, site_id, user)
    allowed = {"off", "essentially_off", "low", "medium", "high", "under_attack"}
    if payload.level not in allowed:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "安全级别不合法")
    try:
        level = await asyncio.to_thread(
            cloudflare.set_security_level, row.token, row.zone_id, payload.level
        )
    except cloudflare.CloudflareError as exc:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"设置失败：{exc}") from exc
    return {"level": level, "under_attack": level == "under_attack"}
