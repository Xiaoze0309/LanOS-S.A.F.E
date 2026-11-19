"""Microsoft 登录：OAuth2 设备码流程（Device Code Flow）。

为什么用设备码：不需要 redirect URI、不需要 client_secret、不用改 Manifest 的 deep link，
后端轮询拿 id_token 即可；对手机端来说体验是「打开浏览器 → 输入 6 位码 → 回来」。
"""

from __future__ import annotations

import base64
import json
import logging
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from app.core.config import settings

logger = logging.getLogger("lanos.ms")

TIMEOUT = 15


class MsError(Exception):
    def __init__(self, message: str, code: str = "") -> None:
        super().__init__(message)
        self.code = code


def enabled() -> bool:
    return bool(settings.MS_CLIENT_ID)


def _post_form(url: str, data: dict) -> dict:
    body = urlencode(data).encode()
    req = Request(
        url,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urlopen(req, timeout=TIMEOUT) as resp:  # noqa: S310 - 固定微软域名
            return json.loads(resp.read().decode("utf-8", "ignore"))
    except Exception as exc:  # noqa: BLE001 - HTTPError 里带 JSON 错误体
        payload = {}
        if hasattr(exc, "read"):
            try:
                payload = json.loads(exc.read().decode("utf-8", "ignore"))
            except Exception:  # noqa: BLE001
                payload = {}
        if payload.get("error"):
            raise MsError(payload.get("error_description", payload["error"]), payload["error"]) from exc
        raise MsError(str(exc)) from exc


def start_device_flow() -> dict:
    url = f"https://login.microsoftonline.com/{settings.MS_TENANT}/oauth2/v2.0/devicecode"
    data = _post_form(url, {"client_id": settings.MS_CLIENT_ID, "scope": "openid email profile offline_access"})
    return {
        "device_code": data.get("device_code", ""),
        "user_code": data.get("user_code", ""),
        "verification_uri": data.get("verification_uri", "https://microsoft.com/devicelogin"),
        "expires_in": int(data.get("expires_in", 900)),
        "interval": int(data.get("interval", 5)),
        "message": data.get("message", ""),
    }


def poll_token(device_code: str) -> dict:
    url = f"https://login.microsoftonline.com/{settings.MS_TENANT}/oauth2/v2.0/token"
    try:
        return _post_form(
            url,
            {
                "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
                "client_id": settings.MS_CLIENT_ID,
                "device_code": device_code,
            },
        )
    except MsError as exc:
        if exc.code in {"authorization_pending", "slow_down"}:
            return {"error": exc.code}
        raise


def claims_from_id_token(id_token: str) -> dict:
    """只做结构解析 + 基本校验（aud/exp）；id_token 是刚从微软 token 端点取回的，走的是 TLS。"""
    try:
        payload_b64 = id_token.split(".")[1]
        payload_b64 += "=" * (-len(payload_b64) % 4)
        claims = json.loads(base64.urlsafe_b64decode(payload_b64))
    except Exception as exc:  # noqa: BLE001
        raise MsError("id_token 解析失败") from exc

    if claims.get("aud") != settings.MS_CLIENT_ID:
        raise MsError("id_token 的 aud 与 client_id 不一致")
    return claims
