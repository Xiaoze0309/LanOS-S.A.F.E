"""流量时间聚合：把分钟级原始点按查询区间折叠成等宽桶，并补齐空桶。"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

# range -> (窗口秒数, 桶宽秒数, 中文标签)
RANGES: dict[str, tuple[int, int, str]] = {
    "30m": (30 * 60, 60, "近 30 分钟"),
    "1h": (60 * 60, 60, "近 1 小时"),
    "3h": (3 * 3600, 300, "近 3 小时"),
    "6h": (6 * 3600, 600, "近 6 小时"),
    "12h": (12 * 3600, 900, "近 12 小时"),
    "24h": (24 * 3600, 1800, "近 24 小时"),
    "1m": (30 * 86400, 86400, "近 1 个月"),
    "3m": (90 * 86400, 86400, "近 3 个月"),
    "6m": (180 * 86400, 86400, "近 6 个月"),
    "1y": (365 * 86400, 30 * 86400, "近 1 年"),
}

DEFAULT_RANGE = "24h"


def resolve(range_key: str | None) -> tuple[int, int]:
    span, bucket, _ = RANGES.get(range_key or DEFAULT_RANGE, RANGES[DEFAULT_RANGE])
    return span, bucket


def utcnow() -> datetime:
    """统一用 UTC naive 时间入库，避免容器时区差异导致统计漂移。"""
    return datetime.now(timezone.utc).replace(tzinfo=None)


def floor_ts(ts: datetime, bucket: int) -> int:
    epoch = int(ts.replace(tzinfo=timezone.utc).timestamp())
    return epoch - epoch % bucket


def fill(
    rows: list[tuple[int, int, int, int]],
    *,
    range_key: str,
    now: datetime | None = None,
) -> list[dict]:
    """rows = [(bucket_epoch, requests, bytes, blocked)] → 补零后的连续序列。"""
    span, bucket = resolve(range_key)
    now = now or utcnow()
    end = floor_ts(now, bucket)
    start = floor_ts(now - timedelta(seconds=span), bucket)

    merged: dict[int, list[int]] = {}
    for epoch, requests, byte_count, blocked in rows:
        slot = int(epoch) - int(epoch) % bucket
        acc = merged.setdefault(slot, [0, 0, 0])
        acc[0] += int(requests or 0)
        acc[1] += int(byte_count or 0)
        acc[2] += int(blocked or 0)

    points: list[dict] = []
    cursor = start
    while cursor <= end:
        acc = merged.get(cursor, [0, 0, 0])
        points.append(
            {"t": cursor, "requests": acc[0], "bytes": acc[1], "blocked": acc[2]}
        )
        cursor += bucket
    return points
