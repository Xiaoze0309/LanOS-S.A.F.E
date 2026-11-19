from __future__ import annotations

import hmac
import json
import logging
import secrets
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import current_user
from app.core.config import settings
from app.core.mailer import MailError, send_code
from app.core.security import (
    JWTError,
    backup_code_hash,
    code_hash,
    decode_jwt,
    dummy_hash,
    encode_jwt,
    generate_backup_codes,
    generate_totp_secret,
    hash_password,
    hash_password_async,
    hash_token,
    random_code,
    totp_uri,
    verify_password,
    verify_password_async,
    verify_totp,
)
from app.db.models import EmailCode, RefreshToken, User
from app.db.session import get_db
from app.schemas import (
    CodeIn,
    LoginIn,
    MfaIn,
    MfaTokenIn,
    PasswordIn,
    RefreshIn,
    RegisterIn,
    RegisterVerifyIn,
    ResetCodeIn,
    ResetIn,
)
from app.services.buckets import utcnow
from app.services.ratelimit import login_limiter, register_limiter

router = APIRouter(prefix="/auth", tags=["auth"])
logger = logging.getLogger("lanos.auth")

# 预计算一次，用户不存在时也跑一次 PBKDF2，避免用响应时间枚举邮箱
_DUMMY_HASH = dummy_hash()


def _dev(payload: dict, code: str) -> dict:
    if settings.DEV_MODE:
        payload["dev_code"] = code
    return payload


async def _issue_tokens(db: AsyncSession, user: User) -> dict:
    access = encode_jwt(
        {"sub": str(user.id), "scope": "access", "email": user.email},
        settings.SECRET_KEY,
        settings.ACCESS_TOKEN_TTL,
    )
    raw_refresh = secrets.token_urlsafe(32)
    db.add(
        RefreshToken(
            user_id=user.id,
            token_hash=hash_token(raw_refresh),
            expires_at=utcnow() + timedelta(seconds=settings.REFRESH_TOKEN_TTL),
        )
    )
    await db.commit()
    return {
        "access_token": access,
        "refresh_token": raw_refresh,
        "token_type": "bearer",
        "expires_in": settings.ACCESS_TOKEN_TTL,
    }


async def _new_code(db: AsyncSession, email: str, purpose: str) -> str:
    code = random_code()
    db.add(
        EmailCode(
            email=email,
            purpose=purpose,
            code_hash=code_hash(code, settings.SECRET_KEY),
            expires_at=utcnow() + timedelta(seconds=settings.EMAIL_CODE_TTL),
        )
    )
    await db.commit()
    try:
        await send_code(email, code, purpose)
    except MailError as exc:
        if settings.DEV_MODE:
            logger.warning("邮件发送失败（DEV_MODE 下继续，验证码 %s）：%s", code, exc)
        else:
            raise HTTPException(
                status.HTTP_502_BAD_GATEWAY,
                "验证码邮件发送失败，请稍后重试；若持续失败请联系管理员",
            ) from exc
    return code


async def _consume_code(db: AsyncSession, email: str, purpose: str, code: str) -> None:
    stmt = (
        select(EmailCode)
        .where(
            EmailCode.email == email,
            EmailCode.purpose == purpose,
            EmailCode.consumed_at.is_(None),
            EmailCode.expires_at > utcnow(),
        )
        .order_by(EmailCode.id.desc())
        .limit(1)
    )
    row = (await db.execute(stmt)).scalars().first()
    if row is None:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "验证码已过期，请重新获取")
    if row.attempts >= 5:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "验证码尝试次数过多，请重新获取")
    row.attempts += 1
    if not hmac.compare_digest(row.code_hash, code_hash(code, settings.SECRET_KEY)):
        await db.commit()
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "验证码不正确")
    row.consumed_at = utcnow()
    await db.commit()


async def _load_mfa_user(db: AsyncSession, mfa_token: str) -> User:
    try:
        payload = decode_jwt(mfa_token, settings.SECRET_KEY)
    except JWTError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, f"验证会话已失效：{exc}") from exc
    if payload.get("scope") != "mfa":
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "验证会话类型不正确")
    try:
        user_id = int(payload["sub"])
    except (KeyError, TypeError, ValueError) as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "验证会话无效") from exc
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "账号不存在")
    return user


def _try_backup_code(user: User, code: str) -> bool:
    if not user.backup_codes:
        return False
    try:
        hashes: list[str] = json.loads(user.backup_codes)
    except (TypeError, ValueError):
        return False
    target = backup_code_hash(code.strip(), settings.SECRET_KEY)
    for index, stored in enumerate(hashes):
        if hmac.compare_digest(stored, target):
            hashes.pop(index)
            user.backup_codes = json.dumps(hashes)
            return True
    return False


@router.post("/register", status_code=status.HTTP_202_ACCEPTED)
async def register(payload: RegisterIn, db: AsyncSession = Depends(get_db)) -> dict:
    email = payload.email
    if not register_limiter.allow(email):
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试")

    user = (await db.execute(select(User).where(User.email == email))).scalars().first()
    if user is not None and user.is_active:
        raise HTTPException(status.HTTP_409_CONFLICT, "该邮箱已注册，请直接登录")
    if user is not None:
        user.password_hash = await hash_password_async(payload.password)
    else:
        user = User(email=email, password_hash=await hash_password_async(payload.password), is_active=False)
        db.add(user)
    await db.commit()

    code = await _new_code(db, email, "register")
    return _dev({"message": "验证码已发送到邮箱", "email": email}, code)


@router.post("/register/verify")
async def register_verify(payload: RegisterVerifyIn, db: AsyncSession = Depends(get_db)) -> dict:
    user = (await db.execute(select(User).where(User.email == payload.email))).scalars().first()
    if user is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "请先提交注册信息")
    await _consume_code(db, payload.email, "register", payload.code)
    user.is_active = True
    await db.commit()
    return await _issue_tokens(db, user)


@router.post("/login")
async def login(payload: LoginIn, db: AsyncSession = Depends(get_db)) -> dict:
    if not login_limiter.allow(payload.email):
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "登录尝试过多，请 10 分钟后再试")

    user = (await db.execute(select(User).where(User.email == payload.email))).scalars().first()
    stored = user.password_hash if user is not None else _DUMMY_HASH
    password_ok = await verify_password_async(payload.password, stored)
    if user is None or not password_ok:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "邮箱或密码错误")
    if not user.is_active:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "邮箱尚未验证，请完成注册验证")

    if user.totp_enabled:
        mfa_token = encode_jwt(
            {"sub": str(user.id), "scope": "mfa", "method": "totp"},
            settings.SECRET_KEY,
            settings.MFA_TOKEN_TTL,
        )
        return {"mfa_required": True, "mfa_token": mfa_token, "methods": ["totp", "email"]}

    return await _issue_tokens(db, user)


@router.post("/login/totp")
async def login_totp(payload: MfaIn, db: AsyncSession = Depends(get_db)) -> dict:
    user = await _load_mfa_user(db, payload.mfa_token)
    if not user.totp_enabled or not user.totp_secret:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "该账号未开启动态口令")
    if not verify_totp(user.totp_secret, payload.code):
        if not _try_backup_code(user, payload.code):
            raise HTTPException(status.HTTP_400_BAD_REQUEST, "动态验证码不正确")
        await db.commit()
    return await _issue_tokens(db, user)


@router.post("/login/email-code")
async def login_email_code(payload: MfaTokenIn, db: AsyncSession = Depends(get_db)) -> dict:
    user = await _load_mfa_user(db, payload.mfa_token)
    if not register_limiter.allow(f"mfa:{user.email}"):
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "验证码发送过于频繁")
    code = await _new_code(db, user.email, "login")
    return _dev({"message": "验证码已发送到邮箱"}, code)


@router.post("/login/email-code/verify")
async def login_email_code_verify(payload: MfaIn, db: AsyncSession = Depends(get_db)) -> dict:
    user = await _load_mfa_user(db, payload.mfa_token)
    await _consume_code(db, user.email, "login", payload.code)
    return await _issue_tokens(db, user)


@router.post("/password/reset-code", status_code=status.HTTP_202_ACCEPTED)
async def request_password_reset(
    payload: ResetCodeIn, db: AsyncSession = Depends(get_db)
) -> dict:
    """无论邮箱是否存在都返回同样的结果，避免被用来枚举用户。"""
    if not register_limiter.allow(f"reset:{payload.email}"):
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试")

    user = (await db.execute(select(User).where(User.email == payload.email))).scalars().first()
    resp = {"message": "如果该邮箱已注册，验证码会在几分钟内送达"}
    if user is not None:
        code = await _new_code(db, payload.email, "reset")
        if settings.DEV_MODE:
            resp["dev_code"] = code
    return resp


@router.post("/password/reset")
async def reset_password(payload: ResetIn, db: AsyncSession = Depends(get_db)) -> dict:
    user = (await db.execute(select(User).where(User.email == payload.email))).scalars().first()
    if user is None:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "邮箱或验证码不正确")

    await _consume_code(db, payload.email, "reset", payload.code)

    user.password_hash = await hash_password_async(payload.new_password)
    user.is_active = True

    # 改密后所有旧会话立即失效
    for row in (
        await db.execute(
            select(RefreshToken).where(
                RefreshToken.user_id == user.id, RefreshToken.revoked_at.is_(None)
            )
        )
    ).scalars().all():
        row.revoked_at = utcnow()
    await db.commit()
    return {"message": "密码已重置，请用新密码登录"}


@router.post("/refresh")
async def refresh(payload: RefreshIn, db: AsyncSession = Depends(get_db)) -> dict:
    row = (
        await db.execute(
            select(RefreshToken).where(
                RefreshToken.token_hash == hash_token(payload.refresh_token),
                RefreshToken.revoked_at.is_(None),
                RefreshToken.expires_at > utcnow(),
            )
        )
    ).scalars().first()
    if row is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "刷新令牌无效或已过期")
    user = await db.get(User, row.user_id)
    if user is None or not user.is_active:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "账号不可用")
    row.revoked_at = utcnow()
    await db.commit()
    return await _issue_tokens(db, user)


@router.post("/logout")
async def logout(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> dict:
    rows = (
        await db.execute(
            select(RefreshToken).where(
                RefreshToken.user_id == user.id, RefreshToken.revoked_at.is_(None)
            )
        )
    ).scalars().all()
    for row in rows:
        row.revoked_at = utcnow()
    await db.commit()
    return {"message": "已退出登录"}


@router.get("/me")
async def me(user: User = Depends(current_user)) -> dict:
    return {
        "id": user.id,
        "email": user.email,
        "totp_enabled": user.totp_enabled,
        "created_at": user.created_at.isoformat(sep=" ") if user.created_at else None,
    }


@router.post("/2fa/setup")
async def setup_2fa(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> dict:
    secret = generate_totp_secret()
    codes = generate_backup_codes()
    user.totp_secret = secret
    user.backup_codes = json.dumps([backup_code_hash(c, settings.SECRET_KEY) for c in codes])
    await db.commit()
    return {
        "secret": secret,
        "otpauth_uri": totp_uri(secret, user.email),
        "issuer": "LanOS S.A.F.E",
        "backup_codes": codes,
    }


@router.post("/2fa/enable")
async def enable_2fa(
    payload: CodeIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    if not user.totp_secret:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "请先获取密钥")
    if not verify_totp(user.totp_secret, payload.code):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "验证码不正确，请检查手机时间是否准确")
    user.totp_enabled = True
    await db.commit()
    return {"message": "双因素认证已开启"}


@router.post("/2fa/disable")
async def disable_2fa(
    payload: PasswordIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    if not await verify_password_async(payload.password, user.password_hash):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "密码不正确")
    user.totp_enabled = False
    user.totp_secret = None
    user.backup_codes = None
    await db.commit()
    return {"message": "双因素认证已关闭"}
