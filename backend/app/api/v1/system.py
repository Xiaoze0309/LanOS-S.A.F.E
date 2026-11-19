"""系统接口：健康检查、演示数据、以及给 Nginx 用的流量上报入口。"""

from __future__ import annotations

import hmac
import math
import random
import secrets
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import current_user
from app.core.config import settings
from app.db.models import Site, TrafficPoint, User, WafEvent
from app.db.seed import load_rules
from app.db.session import get_db
from app.schemas import IngestIn, TrafficAggregateIn
from app.services import buckets, waf

router = APIRouter(tags=["system"])

rate_tracker = waf.RateTracker(limit=180, window=60)

VERSION = "2.5.0"

DEMO_ATTACKS = [
    ("/wp-login.php", "sqlmap/1.7.2#stable", "敏感文件探测", "block", "high"),
    ("/?id=1%20union%20select%20user,pass%20from%20users", "Mozilla/5.0", "SQL 注入", "block", "high"),
    ("/search?q=<script>alert(1)</script>", "Mozilla/5.0 (Windows NT 10.0)", "XSS 跨站脚本", "block", "high"),
    ("/../../../../etc/passwd", "curl/8.4.0", "路径穿越", "block", "high"),
    ("/api/v1/sites", "Bytespider", "恶意爬虫 UA", "challenge", "low"),
    ("/.env", "Mozilla/5.0 (X11; Linux x86_64)", "敏感文件探测", "block", "high"),
    ("/xmlrpc.php", "python-requests/2.31", "命令行工具 UA", "log", "low"),
    ("/admin/../config.php", "Go-http-client/1.1", "路径穿越", "block", "high"),
]


@router.get("/system/health")
async def health(db: AsyncSession = Depends(get_db)) -> dict:
    db_ok = True
    try:
        await db.execute(text("SELECT 1"))
    except Exception:  # noqa: BLE001 - 数据库挂了也要能返回 JSON
        db_ok = False
    return {
        "ok": db_ok,
        "service": "lanos-safe",
        "version": VERSION,
        "database": "up" if db_ok else "down",
        "time": buckets.utcnow().isoformat(sep=" "),
    }


def _series(base: int, count: int, phase: float = 0.0) -> list[int]:
    out = []
    for i in range(count):
        wave = 0.55 + 0.45 * math.sin((i / max(1, count - 1)) * math.pi * 2 + phase)
        out.append(max(1, int(base * wave) + random.randint(0, max(1, base // 3))))
    return out


@router.post("/system/demo/seed")
async def seed_demo(
    user: User = Depends(current_user), db: AsyncSession = Depends(get_db)
) -> dict:
    """生成一年份的演示流量与攻击事件（仅 DEV_MODE）。生产环境请关掉 DEV_MODE。"""
    if not settings.DEV_MODE:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "演示数据接口仅在 DEV_MODE 下可用")

    sites = (
        await db.execute(select(Site).where(Site.user_id == user.id))
    ).scalars().all()
    if not sites:
        site = Site(
            user_id=user.id,
            domain="demo.lanos.local",
            status="verified",
            verify_token=f"lanos-verify-{secrets.token_hex(16)}",
        )
        db.add(site)
        await db.commit()
        await db.refresh(site)
        sites = [site]

    now = buckets.utcnow().replace(second=0, microsecond=0)
    inserted = 0
    for site in sites:
        # 清空旧演示数据，避免重复叠加
        for row in (
            await db.execute(select(TrafficPoint).where(TrafficPoint.site_id == site.id))
        ).scalars().all():
            await db.delete(row)
        await db.commit()

        # 用 dict 去重：三组序列在边界上会落在同一分钟，直接 add_all 会撞唯一键
        points: dict = {}

        def put(ts, value: int, blocked: int) -> None:
            points[ts] = TrafficPoint(
                site_id=site.id,
                ts=ts,
                requests=value,
                bytes=value * random.randint(1800, 4200),
                blocked=blocked,
            )

        # 近 60 分钟（分钟级，给 30m/1h 视图）
        for i, value in enumerate(_series(26, 60, phase=0.4)):
            put(now - timedelta(minutes=59 - i), value, random.randint(0, 3))

        # 1 小时前 ~ 25 小时前（10 分钟粒度，给 3h/6h/12h/24h 视图）
        for i, value in enumerate(_series(90, 144, phase=1.1)):
            put(now - timedelta(minutes=70 + i * 10), value, random.randint(0, 9))

        # 近 365 天（天粒度，给 1m/3m/6m/1y 视图）；整体后移 30 分钟避开上面两组
        for i, value in enumerate(_series(2400, 365, phase=2.2)):
            put(now - timedelta(days=364 - i) - timedelta(minutes=30), value, random.randint(0, 60))

        db.add_all(list(points.values()))
        inserted += len(points)

        # 攻击事件
        for k in range(28):
            path, ua, rule_name, action, severity = random.choice(DEMO_ATTACKS)
            db.add(
                WafEvent(
                    site_id=site.id,
                    ip=f"{random.choice([45, 91, 103, 141, 185])}.{random.randint(1, 254)}.{random.randint(1, 254)}.{random.randint(1, 254)}",
                    method=random.choice(["GET", "POST", "HEAD"]),
                    path=path,
                    ua=ua,
                    action=action,
                    rule_name=rule_name,
                    score={"low": 1, "medium": 2, "high": 3}.get(severity, 2),
                    created_at=now - timedelta(minutes=random.randint(1, 900)),
                )
            )
    await db.commit()
    return {"message": "演示数据已生成", "traffic_points": inserted, "sites": len(sites)}


@router.post("/traffic/aggregate")
async def ingest_aggregate(
    payload: TrafficAggregateIn,
    x_ingest_key: str = Header(default="", alias="X-Ingest-Key"),
    db: AsyncSession = Depends(get_db),
) -> dict:
    """批量导入聚合流量（Cloudflare Analytics / 其他 CDN 的小时级数据）。

    body: {"domain":"example.com","points":[{"t":1795000000,"requests":120,"bytes":98000}]}
    """
    if not hmac.compare_digest(x_ingest_key, settings.INGEST_KEY):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "上报密钥不正确")

    site = (await db.execute(select(Site).where(Site.domain == payload.domain))).scalars().first()
    if site is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "域名未接入")

    written = 0
    for point in payload.points[:5000]:
        ts = datetime.fromtimestamp(point.t, tz=timezone.utc).replace(
            tzinfo=None, second=0, microsecond=0
        )
        row = (
            await db.execute(
                select(TrafficPoint).where(
                    TrafficPoint.site_id == site.id, TrafficPoint.ts == ts
                )
            )
        ).scalars().first()
        if row is None:
            db.add(
                TrafficPoint(
                    site_id=site.id,
                    ts=ts,
                    requests=max(0, point.requests),
                    bytes=max(0, point.bytes),
                    blocked=max(0, point.blocked),
                )
            )
        else:
            row.requests += max(0, point.requests)
            row.bytes += max(0, point.bytes)
            row.blocked += max(0, point.blocked)
        written += 1
    await db.commit()
    return {"domain": site.domain, "points": written}


@router.post("/ingest")
async def ingest(
    payload: IngestIn,
    x_ingest_key: str = Header(default="", alias="X-Ingest-Key"),
    db: AsyncSession = Depends(get_db),
) -> dict:
    """Nginx/OpenResty 的 access_log 通过它上报：一条日志 = 一次请求。

    建议在站点配置里：
        log_by_lua_file /etc/nginx/lanos_ingest.lua;
    由脚本批量 POST 到本接口，带上 X-Ingest-Key。
    """
    if not hmac.compare_digest(x_ingest_key, settings.INGEST_KEY):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "上报密钥不正确")

    site = (
        await db.execute(select(Site).where(Site.domain == payload.domain))
    ).scalars().first()
    if site is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "域名未接入")

    rules = await load_rules(db)
    decision = waf.inspect(
        rules, path=payload.path, ua=payload.ua, method=payload.method, query=payload.path
    )

    over_limit, hits = rate_tracker.hit(payload.ip or "-")
    if over_limit:
        decision.verdict = "block"
        decision.matches.append(
            {"rule_id": None, "name": "CC 频率超限", "action": "block", "severity": "high"}
        )
        decision.score = min(99, decision.score + 3)

    minute = buckets.utcnow().replace(second=0, microsecond=0)
    row = (
        await db.execute(
            select(TrafficPoint).where(
                TrafficPoint.site_id == site.id, TrafficPoint.ts == minute
            )
        )
    ).scalars().first()
    blocked = 1 if decision.verdict == "block" else 0
    if row is None:
        db.add(
            TrafficPoint(
                site_id=site.id,
                ts=minute,
                requests=1,
                bytes=max(0, payload.bytes),
                blocked=blocked,
            )
        )
    else:
        row.requests += 1
        row.bytes += max(0, payload.bytes)
        row.blocked += blocked

    if decision.verdict != "allow":
        first = decision.matches[0] if decision.matches else {}
        db.add(
            WafEvent(
                site_id=site.id,
                ip=payload.ip[:64],
                method=payload.method[:8],
                path=payload.path[:512],
                ua=payload.ua[:512],
                action=decision.verdict,
                rule_id=first.get("rule_id"),
                rule_name=str(first.get("name") or "")[:96],
                score=decision.score,
            )
        )

    await db.commit()
    return {
        "verdict": decision.verdict,
        "score": decision.score,
        "matched": [m["name"] for m in decision.matches],
        "hits_last_minute": hits,
    }
