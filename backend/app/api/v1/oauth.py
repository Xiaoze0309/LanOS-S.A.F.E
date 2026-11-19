from __future__ import annotations

import asyncio
import logging
import secrets

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.auth import _issue_tokens
from app.core.config import settings
from app.core.security import hash_password_async
from app.db.models import OauthLink, User
from app.db.session import get_db
from app.services import ms_oauth

router = APIRouter(prefix="/auth/oauth/microsoft", tags=["auth"])
logger = logging.getLogger("lanos.ms")


class DevicePollIn(BaseModel):
    device_code: str


async def _upsert_user(db: AsyncSession, claims: dict) -> User:
    subject = str(claims.get("oid") or claims.get("sub") or "")
    email = (claims.get("email") or claims.get("preferred_username") or "").strip().lower()
    if not subject or not email:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "微软账号未返回可用的邮箱")

    link = (
        await db.execute(
            select(OauthLink).where(
                OauthLink.provider == "microsoft", OauthLink.subject == subject
            )
        )
    ).scalars().first()
    if link is not None:
        user = await db.get(User, link.user_id)
        if user is not None:
            return user

    user = (await db.execute(select(User).where(User.email == email))).scalars().first()
    if user is None:
        # 随机密码 + 标记激活：之后用户可以用「忘记密码」设置自己的密码
        user = User(
            email=email,
            password_hash=await hash_password_async(secrets.token_urlsafe(32)),
            is_active=True,
        )
        db.add(user)
        await db.flush()
    elif not user.is_active:
        user.is_active = True

    db.add(OauthLink(user_id=user.id, provider="microsoft", subject=subject, email=email))
    await db.commit()
    await db.refresh(user)
    return user


@router.get("/config")
async def ms_config() -> dict:
    return {
        "enabled": ms_oauth.enabled(),
        "tenant": settings.MS_TENANT,
        "flow": "device_code",
    }


@router.post("/device/start")
async def device_start() -> dict:
    if not ms_oauth.enabled():
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "服务端未配置 Microsoft 登录（MS_CLIENT_ID）")
    try:
        data = await asyncio.to_thread(ms_oauth.start_device_flow)
    except ms_oauth.MsError as exc:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"无法连接微软登录：{exc}") from exc
    return data


@router.post("/device/poll")
async def device_poll(payload: DevicePollIn, db: AsyncSession = Depends(get_db)) -> dict:
    if not ms_oauth.enabled():
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "服务端未配置 Microsoft 登录")

    try:
        token = await asyncio.to_thread(ms_oauth.poll_token, payload.device_code)
    except ms_oauth.MsError as exc:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"微软登录失败：{exc}") from exc

    if token.get("error") in {"authorization_pending", "slow_down"}:
        return {"status": "pending"}
    if token.get("error"):
        return {"status": "denied", "message": token.get("error_description", token["error"])}
    if not token.get("id_token"):
        return {"status": "pending"}

    claims = ms_oauth.claims_from_id_token(token["id_token"])
    user = await _upsert_user(db, claims)
    tokens = await _issue_tokens(db, user)
    return {"status": "ok", "email": user.email, **tokens}
