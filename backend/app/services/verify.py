"""域名归属校验：DNS TXT 记录 或 站点根目录校验文件，二者其一即可。

TXT 记录（推荐）：
    _lanos-verify.example.com   TXT   "lanos-verify=<token>"
校验文件：
    http://example.com/.well-known/lanos-verify.txt   内容为 token
"""

from __future__ import annotations

import asyncio
import logging
from urllib.request import Request, urlopen

logger = logging.getLogger("lanos.verify")


def _dns_txt(domain: str, token: str) -> bool:
    try:
        import dns.resolver  # type: ignore
    except ImportError:
        logger.info("未安装 dnspython，跳过 DNS 校验")
        return False
    try:
        answers = dns.resolver.resolve(f"_lanos-verify.{domain}", "TXT", lifetime=4.0)
    except Exception as exc:  # noqa: BLE001 - NXDOMAIN / 超时都算未通过
        logger.info("DNS 校验失败 %s: %s", domain, exc)
        return False

    for record in answers:
        try:
            text = b"".join(record.strings).decode("utf-8", "ignore")
        except AttributeError:
            text = str(record)
        if token in text:
            return True
    return False


def _http_file(domain: str, token: str) -> bool:
    for scheme in ("https", "http"):
        url = f"{scheme}://{domain}/.well-known/lanos-verify.txt"
        try:
            request = Request(url, headers={"User-Agent": "LanOS-Verifier/2.0"})
            with urlopen(request, timeout=6) as response:  # noqa: S310 - 用户自己的域名
                body = response.read(4096).decode("utf-8", "ignore").strip()
                if body == token or body.endswith(token):
                    return True
        except Exception as exc:  # noqa: BLE001
            logger.info("HTTP 校验失败 %s: %s", url, exc)
    return False


async def verify_domain(domain: str, token: str) -> dict:
    dns_ok, http_ok = await asyncio.gather(
        asyncio.to_thread(_dns_txt, domain, token),
        asyncio.to_thread(_http_file, domain, token),
    )
    verified = bool(dns_ok or http_ok)
    if verified:
        message = "校验通过，域名已接入"
    elif dns_ok is False and http_ok is False:
        message = "未检测到校验记录：请确认 TXT 已生效（可能需要几分钟）或校验文件可访问"
    else:
        message = "校验未通过"
    return {
        "verified": verified,
        "dns_ok": bool(dns_ok),
        "http_ok": bool(http_ok),
        "message": message,
    }
