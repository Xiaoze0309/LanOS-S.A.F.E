"""Cloudflare API：校验 token / 查 zone / 拉小时级统计（GraphQL Analytics）。"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

API = "https://api.cloudflare.com/client/v4"
TIMEOUT = 20

QUERY = """
query($zone: String!, $start: Time!, $end: Time!) {
  viewer {
    zones(filter: {zoneTag: $zone}) {
      httpRequests1hGroups(limit: 48, filter: {datetime_geq: $start, datetime_leq: $end}, orderBy: [datetime_ASC]) {
        dimensions { datetime }
        sum { requests bytes }
      }
    }
  }
}
"""


class CloudflareError(Exception):
    pass


def _request(url: str, token: str, body: dict | None = None, method: str | None = None) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method=method or ("POST" if body is not None else "GET"),
    )
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "ignore")[:200]
        raise CloudflareError(f"HTTP {exc.code}: {detail}") from exc
    except Exception as exc:  # noqa: BLE001
        raise CloudflareError(str(exc)) from exc


def verify_zone(token: str, zone_id: str) -> str:
    """返回 zone 名称；token 或 zone 不对会抛 CloudflareError。"""
    data = _request(f"{API}/zones/{zone_id}", token)
    if not data.get("success"):
        raise CloudflareError(_first_error(data) or "token 无效或没有该 zone 权限")
    return (data.get("result") or {}).get("name", "")


def _first_error(data: dict) -> str:
    errors = data.get("errors") or []
    if errors and isinstance(errors[0], dict):
        return errors[0].get("message", "")
    return ""


def hourly_points(token: str, zone_id: str, hours: int = 6) -> list[dict]:
    end = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    start = end - timedelta(hours=hours)
    fmt = "%Y-%m-%dT%H:%M:%SZ"
    data = _request(
        f"{API}/graphql",
        token,
        {"query": QUERY, "variables": {"zone": zone_id, "start": start.strftime(fmt), "end": end.strftime(fmt)}},
    )
    if data.get("errors"):
        raise CloudflareError(str(data["errors"])[:200])
    zones = (data.get("data") or {}).get("viewer", {}).get("zones") or []
    groups = zones[0].get("httpRequests1hGroups", []) if zones else []
    out = []
    for g in groups:
        ts = int(datetime.strptime(g["dimensions"]["datetime"], fmt).replace(tzinfo=timezone.utc).timestamp())
        out.append(
            {"t": ts, "requests": int(g["sum"]["requests"]), "bytes": int(g["sum"]["bytes"]), "blocked": 0}
        )
    return out


# ---------------------------------------------------------------- 安全设置 / WAF 规则


def get_security_level(token: str, zone_id: str) -> str:
    data = _request(f"{API}/zones/{zone_id}/settings/security_level", token)
    return ((data.get("result") or {}).get("value")) or "unknown"


def set_security_level(token: str, zone_id: str, level: str) -> str:
    data = _request(
        f"{API}/zones/{zone_id}/settings/security_level",
        token,
        {"value": level},
        method="PATCH",
    )
    if not data.get("success"):
        raise CloudflareError(_first_error(data) or "设置安全级别失败")
    return ((data.get("result") or {}).get("value")) or level


def push_custom_rules(token: str, zone_id: str, rules: list[dict]) -> dict:
    """覆盖写入 http_request_firewall_custom 阶段的规则（免费版最多 5 条）。"""
    url = f"{API}/zones/{zone_id}/rulesets/phases/http_request_firewall_custom/entrypoint"
    data = _request(url, token, {"rules": rules}, method="PUT")
    if not data.get("success"):
        raise CloudflareError(_first_error(data) or "下发规则失败（检查 token 是否有 Zone.WAF:Edit）")
    result = data.get("result") or {}
    return {"rules": len(result.get("rules", []) or []), "id": result.get("id", "")}
