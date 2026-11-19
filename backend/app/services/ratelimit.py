"""进程内限流：够单机自用；多 worker 时改成 Redis 即可。"""

from __future__ import annotations

import threading
import time
from collections import defaultdict, deque


class Limiter:
    def __init__(self, limit: int, window: float) -> None:
        self.limit = limit
        self.window = window
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        with self._lock:
            q = self._hits[key]
            while q and now - q[0] > self.window:
                q.popleft()
            if len(q) >= self.limit:
                return False
            q.append(now)
            if len(self._hits) > 50_000:
                self._hits = defaultdict(deque, {k: v for k, v in self._hits.items() if v and now - v[-1] <= self.window})
            return True


# 单邮箱注册/发码：10 分钟 5 次
register_limiter = Limiter(limit=5, window=600)
# 单邮箱登录尝试：10 分钟 10 次
login_limiter = Limiter(limit=10, window=600)
# 单 IP 的通用 API 兜底
api_limiter = Limiter(limit=240, window=60)
