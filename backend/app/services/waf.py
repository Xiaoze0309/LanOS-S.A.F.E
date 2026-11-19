"""WAF 规则引擎（纯 Python，无框架依赖，便于单测与复用）。

判定顺序：命中 block > challenge > log；分数按严重度累加。
真实拦截点建议放在 Nginx/边缘（见 /api/v1/waf/edge/nginx），本引擎既做线上判定，
也作为控制面的“规则试跑”能力。
"""

from __future__ import annotations

import re
import threading
import time
from collections import deque
from dataclasses import dataclass, field
from functools import lru_cache

SEVERITY_SCORE = {"low": 1, "medium": 2, "high": 3}
ACTION_RANK = {"log": 0, "challenge": 1, "block": 2}
TARGETS = {"path", "ua", "query", "body"}
METHODS = {"GET", "POST", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS"}


@dataclass(frozen=True)
class Rule:
    name: str
    pattern: str
    target: str = "path"
    action: str = "block"
    severity: str = "medium"
    rule_id: int | None = None


@dataclass
class Decision:
    verdict: str = "allow"
    score: int = 0
    matches: list[dict] = field(default_factory=list)

    @property
    def blocked(self) -> bool:
        return self.verdict == "block"

    def as_dict(self) -> dict:
        return {"verdict": self.verdict, "score": self.score, "matches": self.matches}


@lru_cache(maxsize=512)
def _compiled(pattern: str):
    return re.compile(pattern)


def _safe_match(rule: Rule, value: str) -> bool:
    if not value:
        return False
    try:
        return _compiled(rule.pattern).search(value) is not None
    except re.error:
        # 规则写错不应导致整个网关 500：跳过该条，交由控制面修正
        return False


def inspect(
    rules: list[Rule],
    *,
    path: str = "/",
    ua: str = "",
    query: str = "",
    body: str = "",
    method: str = "GET",
) -> Decision:
    decision = Decision()
    subject = {"path": path, "ua": ua, "query": query, "body": body}

    for rule in rules:
        if rule.target not in TARGETS:
            continue
        if _safe_match(rule, subject.get(rule.target, "")):
            decision.matches.append(
                {
                    "rule_id": rule.rule_id,
                    "name": rule.name,
                    "action": rule.action,
                    "severity": rule.severity,
                }
            )
            decision.score += SEVERITY_SCORE.get(rule.severity, 2)
            if ACTION_RANK.get(rule.action, 0) > ACTION_RANK.get(decision.verdict, -1):
                decision.verdict = rule.action if rule.action in ACTION_RANK else "block"

    # 内建启发式（不依赖规则表，覆盖最常见的扫描器行为）
    if not ua.strip():
        decision.score += 1
        decision.matches.append({"rule_id": None, "name": "空 User-Agent", "action": "challenge", "severity": "low"})
        if decision.verdict == "allow":
            decision.verdict = "challenge"

    if method.upper() not in METHODS:
        decision.score += 3
        decision.matches.append({"rule_id": None, "name": "异常 HTTP 方法", "action": "block", "severity": "high"})
        decision.verdict = "block"

    if "\x00" in path or len(path) > 2048:
        decision.score += 3
        decision.matches.append({"rule_id": None, "name": "畸形请求路径", "action": "block", "severity": "high"})
        decision.verdict = "block"

    decision.score = min(decision.score, 99)
    return decision


class RateTracker:
    """按 IP 的滑动窗口计数，用于识别 CC / 高频爬取。

    仅在单进程内有效；多 worker 部署时应换成 Redis 计数器（这里刻意不引入依赖）。
    """

    def __init__(self, limit: int = 120, window: float = 60.0) -> None:
        self.limit = limit
        self.window = window
        self._hits: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def hit(self, ip: str, now: float | None = None) -> tuple[bool, int]:
        now = time.time() if now is None else now
        with self._lock:
            q = self._hits.setdefault(ip, deque())
            while q and now - q[0] > self.window:
                q.popleft()
            q.append(now)
            count = len(q)
            if len(self._hits) > 20_000:
                self._prune(now)
            return count > self.limit, count

    def _prune(self, now: float) -> None:
        dead = [ip for ip, q in self._hits.items() if not q or now - q[-1] > self.window]
        for ip in dead:
            self._hits.pop(ip, None)


DEFAULT_RULES: list[Rule] = [
    Rule("SQL 注入", r"(?i)(union\s+all\s+select|union\s+select|'\s*or\s*'?1'?\s*=\s*'?1|sleep\s*\(\s*\d|benchmark\s*\(|information_schema)", "path", "block", "high"),
    Rule("XSS 跨站脚本", r"(?i)(<script|javascript:|onerror\s*=|onload\s*=|srcdoc\s*=)", "path", "block", "high"),
    Rule("路径穿越", r"(\.\./|\.\.%2f|%2e%2e/|/etc/passwd|/proc/self/environ)", "path", "block", "high"),
    Rule("命令注入", r"(?i)([;|`]\s*(cat|ls|id|wget|curl|bash|sh)\b|\$\(|&&\s*(cat|wget|curl))", "path", "block", "high"),
    Rule("敏感文件探测", r"(?i)(/\.env|/\.git/|/\.svn/|/wp-login\.php|/xmlrpc\.php|/phpmyadmin|/actuator/|/server-status)", "path", "block", "high"),
    Rule("扫描器特征 UA", r"(?i)(sqlmap|nikto|nmap|masscan|acunetix|nessus|dirbuster|gobuster|wpscan|zgrab)", "ua", "block", "medium"),
    Rule("恶意爬虫 UA", r"(?i)(bytespider|ahrefsbot|semrushbot|mj12bot|dotbot|petalbot)", "ua", "challenge", "low"),
    Rule("命令行工具 UA", r"(?i)(^curl/|^wget/|python-requests|go-http-client|java/\d)", "ua", "log", "low"),
]
