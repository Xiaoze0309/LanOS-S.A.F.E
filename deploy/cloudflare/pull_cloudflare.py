#!/usr/bin/env python3
"""把 Cloudflare 的边缘统计拉进 LanOS（小时级：请求数 / 流量）。

为什么需要它：源站（Nginx）只能看到回源请求；挂了 CF 之后，边缘的请求数、
被 CF 拦截的请求、真实访客 IP 都在 Cloudflare 侧，必须走 API 才能看到。

准备（Cloudflare Dashboard）：
  1. My Profile → API Tokens → Create Token
     权限：Zone → Analytics → Read；Zone → Zone → Read
     资源：只勾你的那个域名
  2. 复制 Zone ID（Overview 页右下角）
  3. 导出环境变量：

    export CF_TOKEN=xxx
    export CF_ZONE_ID=xxx
    export DOMAIN=example.com
    export LANOS_URL=http://127.0.0.1:8000
    export INGEST_KEY=change-me-ingest-key

    python3 pull_cloudflare.py            # 拉最近 24 小时
    python3 pull_cloudflare.py 72         # 拉最近 72 小时

然后建议加个 cron / 定时任务，每小时跑一次：
    0 * * * * cd /opt/lanos && python3 pull_cloudflare.py >> /var/log/lanos-cf.log 2>&1
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

CF_TOKEN = os.environ.get("CF_TOKEN", "")
CF_ZONE_ID = os.environ.get("CF_ZONE_ID", "")
DOMAIN = os.environ.get("DOMAIN", "")
LANOS_URL = os.environ.get("LANOS_URL", "http://127.0.0.1:8000").rstrip("/")
INGEST_KEY = os.environ.get("INGEST_KEY", "")

QUERY = """
query($zone: String!, $start: Time!, $end: Time!) {
  viewer {
    zones(filter: {zoneTag: $zone}) {
      httpRequests1hGroups(
        limit: 168
        filter: {datetime_geq: $start, datetime_leq: $end}
        orderBy: [datetime_ASC]
      ) {
        dimensions { datetime }
        sum { requests bytes }
      }
    }
  }
}
"""


def hours_back(n: int) -> tuple[str, str]:
    end = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    start = end - timedelta(hours=n)
    fmt = "%Y-%m-%dT%H:%M:%SZ"
    return start.strftime(fmt), end.strftime(fmt)


def fetch(start: str, end: str) -> list[dict]:
    body = json.dumps(
        {"query": QUERY, "variables": {"zone": CF_ZONE_ID, "start": start, "end": end}}
    ).encode()
    req = urllib.request.Request(
        "https://api.cloudflare.com/client/v4/graphql",
        data=body,
        headers={
            "Authorization": f"Bearer {CF_TOKEN}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read())
    if data.get("errors"):
        raise SystemExit(f"Cloudflare 返回错误：{data['errors']}")
    zones = data.get("data", {}).get("viewer", {}).get("zones", [])
    if not zones:
        raise SystemExit("没有拿到 zone 数据，检查 CF_ZONE_ID / token 权限")
    return zones[0].get("httpRequests1hGroups", [])


def push(points: list[dict]) -> None:
    for p in points:
        p["t"] = int(
            datetime.strptime(p["t"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc).timestamp()
        )
    body = json.dumps({"domain": DOMAIN, "points": points}).encode()
    req = urllib.request.Request(
        LANOS_URL + "/api/v1/traffic/aggregate",
        data=body,
        headers={"Content-Type": "application/json", "X-Ingest-Key": INGEST_KEY},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        print(resp.read().decode())


def main() -> None:
    missing = [k for k, v in {"CF_TOKEN": CF_TOKEN, "CF_ZONE_ID": CF_ZONE_ID, "DOMAIN": DOMAIN}.items() if not v]
    if missing:
        raise SystemExit(f"缺少环境变量：{', '.join(missing)}")

    hours = int(sys.argv[1]) if len(sys.argv) > 1 else 24
    start, end = hours_back(hours)
    groups = fetch(start, end)
    points = [
        {
            "t": g["dimensions"]["datetime"],
            "requests": int(g["sum"]["requests"]),
            "bytes": int(g["sum"]["bytes"]),
            "blocked": 0,
        }
        for g in groups
    ]
    if not points:
        print("该时间段没有数据")
        return
    print(f"{DOMAIN}: {len(points)} 个小时点，{sum(p['requests'] for p in points)} 次请求")
    push(points)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as exc:
        print("HTTP 错误：", exc.code, exc.read().decode("utf-8", "ignore")[:300])
        raise SystemExit(1)
