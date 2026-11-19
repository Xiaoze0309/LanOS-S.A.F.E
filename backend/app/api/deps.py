"""FastAPI 依赖：Bearer 令牌 → 当前用户。"""

from __future__ import annotations

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import JWTError, decode_jwt
from app.db.models import User
from app.db.session import get_db

bearer = HTTPBearer(auto_error=False)


def _unauthorized(detail: str = "未登录或登录已过期") -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


async def current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: AsyncSession = Depends(get_db),
) -> User:
    if credentials is None or not credentials.credentials:
        raise _unauthorized()
    try:
        payload = decode_jwt(credentials.credentials, settings.SECRET_KEY)
    except JWTError as exc:
        raise _unauthorized(str(exc)) from exc
    if payload.get("scope") != "access":
        raise _unauthorized("令牌类型不正确")
    try:
        user_id = int(payload["sub"])
    except (KeyError, TypeError, ValueError) as exc:
        raise _unauthorized() from exc
    user = await db.get(User, user_id)
    if user is None or not user.is_active:
        raise _unauthorized("账号不存在或未激活")
    return user
