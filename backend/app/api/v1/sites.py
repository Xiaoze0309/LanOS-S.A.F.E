from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.site import Site
from app.db.session import get_db
from app.schemas.site import SiteCreate, SiteOut

router = APIRouter(prefix="/sites", tags=["sites"])


def get_current_user() -> int:
    """模拟当前登录用户，固定返回 id=1。"""
    return 1


@router.get("", response_model=list[SiteOut])
async def list_sites(db: AsyncSession = Depends(get_db)):
    user_id = get_current_user()
    result = await db.execute(select(Site).where(Site.user_id == user_id))
    return result.scalars().all()


@router.post("", response_model=SiteOut, status_code=201)
async def create_site(payload: SiteCreate, db: AsyncSession = Depends(get_db)):
    user_id = get_current_user()
    result = await db.execute(select(Site).where(Site.domain == payload.domain))
    if result.scalar_one_or_none() is not None:
        raise HTTPException(status_code=400, detail="domain already exists")
    site = Site(user_id=user_id, domain=payload.domain)
    db.add(site)
    await db.commit()
    await db.refresh(site)
    return site
