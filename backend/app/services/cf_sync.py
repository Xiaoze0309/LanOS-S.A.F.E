"""后台同步：每隔一段时间把 Cloudflare 的小时统计写进 traffic_points。

不需要用户跑任何脚本 —— 后端启动后自己跑。
"""

from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone

from sqlalchemy import select

from app.db.models import CloudflareIntegration, Site, TrafficPoint
from app.db.session import AsyncSessionLocal
from app.services import cloudflare
from app.services.buckets import utcnow

logger = logging.getLogger("lanos.cf")

INTERVAL = 1800  # 30 分钟


async def sync_once() -> int:
    written = 0
    async with AsyncSessionLocal() as db:
        rows = (
            await db.execute(
                select(CloudflareIntegration).where(CloudflareIntegration.enabled.is_(True))
            )
        ).scalars().all()

        for row in rows:
            site = await db.get(Site, row.site_id)
            if site is None:
                continue
            try:
                points = await asyncio.to_thread(
                    cloudflare.hourly_points, row.token, row.zone_id, 6
                )
            except Exception as exc:  # noqa: BLE001 - 单个站点失败不影响其它
                row.last_error = str(exc)[:250]
                await db.commit()
                logger.warning("CF 同步失败 site=%s: %s", site.domain, exc)
                continue

            for p in points:
                if p["requests"] <= 0:
                    continue
                ts = datetime.fromtimestamp(p["t"], tz=timezone.utc).replace(
                    tzinfo=None, second=0, microsecond=0
                )
                existing = (
                    await db.execute(
                        select(TrafficPoint).where(
                            TrafficPoint.site_id == site.id, TrafficPoint.ts == ts
                        )
                    )
                ).scalars().first()
                if existing is None:
                    db.add(
                        TrafficPoint(
                            site_id=site.id,
                            ts=ts,
                            requests=p["requests"],
                            bytes=p["bytes"],
                            blocked=p["blocked"],
                        )
                    )
                else:
                    # CF 给的是该小时的累计值，重复拉取应当覆盖而不是累加
                    existing.requests = max(existing.requests, p["requests"])
                    existing.bytes = max(existing.bytes, p["bytes"])
                written += 1

            row.last_sync_at = utcnow()
            row.last_error = None
            await db.commit()

    if written:
        logger.info("CF 同步写入 %s 个时间点", written)
    return written


async def cf_sync_loop(stop: asyncio.Event) -> None:
    await asyncio.sleep(15)  # 等建表/迁移完成
    while not stop.is_set():
        try:
            await sync_once()
        except Exception:  # noqa: BLE001
            logger.exception("CF 同步循环异常")
        try:
            await asyncio.wait_for(stop.wait(), timeout=INTERVAL)
        except asyncio.TimeoutError:
            pass
