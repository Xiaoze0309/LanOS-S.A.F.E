"""密码哈希、JWT、TOTP 全部用标准库实现，避免在 ARM 容器里编译第三方加密库。

- 密码：PBKDF2-HMAC-SHA256（260000 轮 + 16 字节随机盐）
- JWT：HS256，手写 40 行，签名为 HMAC-SHA256
- TOTP：RFC 6238，兼容 Microsoft Authenticator / Google Authenticator
"""

from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import secrets
import struct
import time
from typing import Any

PBKDF2_ROUNDS = 260_000
PBKDF2_PREFIX = "pbkdf2_sha256"


# --------------------------------------------------------------------------- 密码


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ROUNDS)
    return "$".join(
        [
            PBKDF2_PREFIX,
            str(PBKDF2_ROUNDS),
            _b64e(salt),
            _b64e(digest),
        ]
    )


def verify_password(password: str, stored: str) -> bool:
    try:
        prefix, rounds, salt_b64, digest_b64 = stored.split("$")
        if prefix != PBKDF2_PREFIX:
            return False
        salt = _b64d(salt_b64)
        expected = _b64d(digest_b64)
        actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, int(rounds))
        return hmac.compare_digest(actual, expected)
    except (ValueError, TypeError):
        return False


def dummy_hash() -> str:
    """用于“用户不存在”时消耗等量算力，避免时序侧信道枚举邮箱。"""
    return hash_password(secrets.token_hex(8))


# --------------------------------------------------------------------------- JWT


class JWTError(Exception):
    pass


def _b64e(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64d(text: str) -> bytes:
    pad = "=" * (-len(text) % 4)
    return base64.urlsafe_b64decode(text + pad)


def encode_jwt(payload: dict[str, Any], secret: str, ttl_seconds: int) -> str:
    now = int(time.time())
    body = dict(payload)
    body.update({"iat": now, "exp": now + int(ttl_seconds)})
    header = {"alg": "HS256", "typ": "JWT"}
    signing_input = ".".join(
        [
            _b64e(json.dumps(header, separators=(",", ":")).encode()),
            _b64e(json.dumps(body, separators=(",", ":")).encode()),
        ]
    )
    signature = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{_b64e(signature)}"


def decode_jwt(token: str, secret: str) -> dict[str, Any]:
    try:
        header_b64, payload_b64, signature_b64 = token.split(".")
    except ValueError as exc:
        raise JWTError("令牌格式错误") from exc

    signing_input = f"{header_b64}.{payload_b64}"
    expected = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    try:
        if not hmac.compare_digest(expected, _b64d(signature_b64)):
            raise JWTError("令牌签名无效")
        payload = json.loads(_b64d(payload_b64))
    except (ValueError, json.JSONDecodeError) as exc:
        raise JWTError("令牌解析失败") from exc

    if int(payload.get("exp", 0)) < int(time.time()):
        raise JWTError("令牌已过期")
    if not payload.get("sub"):
        raise JWTError("令牌缺少主体")
    return payload


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


# --------------------------------------------------------------------------- TOTP

TOTP_STEP = 30
TOTP_DIGITS = 6


def generate_totp_secret() -> str:
    return base64.b32encode(secrets.token_bytes(20)).decode().rstrip("=")


def _totp_key(secret: str) -> bytes:
    pad = "=" * (-len(secret) % 8)
    return base64.b32decode(secret.upper() + pad)


def totp_code(secret: str, at: float | None = None, step: int = TOTP_STEP, digits: int = TOTP_DIGITS) -> str:
    counter = int((time.time() if at is None else at) // step)
    digest = hmac.new(_totp_key(secret), struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    binary = struct.unpack(">I", digest[offset : offset + 4])[0] & 0x7FFFFFFF
    return str(binary % (10**digits)).zfill(digits)


def verify_totp(secret: str, code: str, window: int = 1) -> bool:
    code = (code or "").strip().replace(" ", "")
    if not code.isdigit() or len(code) != TOTP_DIGITS:
        return False
    now = time.time()
    for drift in range(-window, window + 1):
        if hmac.compare_digest(totp_code(secret, at=now + drift * TOTP_STEP), code):
            return True
    return False


def totp_uri(secret: str, account: str, issuer: str = "LanOS S.A.F.E") -> str:
    from urllib.parse import quote

    label = quote(f"{issuer}:{account}")
    params = f"secret={secret}&issuer={quote(issuer)}&algorithm=SHA1&digits={TOTP_DIGITS}&period={TOTP_STEP}"
    return f"otpauth://totp/{label}?{params}"


# --------------------------------------------------------------------------- 验证码 / 备份码


def random_code(digits: int = 6) -> str:
    return "".join(secrets.choice("0123456789") for _ in range(digits))


def code_hash(code: str, secret: str) -> str:
    return hmac.new(secret.encode(), f"code:{code}".encode(), hashlib.sha256).hexdigest()


def backup_code_hash(code: str, secret: str) -> str:
    return hmac.new(secret.encode(), f"backup:{code.upper()}".encode(), hashlib.sha256).hexdigest()


def generate_backup_codes(count: int = 8) -> list[str]:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    codes = []
    for _ in range(count):
        raw = "".join(secrets.choice(alphabet) for _ in range(10))
        codes.append(f"{raw[:5]}-{raw[5:]}")
    return codes


# --------------------------------------------------------------------------- 异步包装
# PBKDF2 是 CPU 密集的（26 万轮），直接在事件循环里跑会阻塞所有请求；
# 统一丢到默认线程池，用法与同步版一致。


async def hash_password_async(password: str) -> str:
    return await asyncio.to_thread(hash_password, password)


async def verify_password_async(password: str, stored: str) -> bool:
    return await asyncio.to_thread(verify_password, password, stored)
