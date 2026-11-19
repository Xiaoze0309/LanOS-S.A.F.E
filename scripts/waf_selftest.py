"""WAF 自测：把一批攻击样本打到 /api/v1/waf/inspect，看判定是否符合预期。

用法（后端已启动、已登录拿到 token）：
    export LANOS_URL=http://127.0.0.1:8000
    export TOKEN=eyJhbGciOi...
    python3 scripts/waf_selftest.py
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

URL = os.environ.get("LANOS_URL", "http://127.0.0.1:8000").rstrip("/")
TOKEN = os.environ.get("TOKEN", "")

CASES = [
    ("正常请求", {"path": "/api/v1/sites", "ua": "Mozilla/5.0 (Linux; Android 16)"}, "allow"),
    ("SQL 注入", {"path": "/item?id=1 union select password from users", "ua": "Mozilla/5.0"}, "block"),
    ("SQL 盲注", {"path": "/?id=1 and sleep(5)", "ua": "Mozilla/5.0"}, "block"),
    ("XSS", {"path": "/search?q=<script>alert(1)</script>", "ua": "Mozilla/5.0"}, "block"),
    ("路径穿越", {"path": "/../../etc/passwd", "ua": "Mozilla/5.0"}, "block"),
    ("命令注入", {"path": "/ping?host=1.1.1.1;cat /etc/passwd", "ua": "Mozilla/5.0"}, "block"),
    ("敏感文件", {"path": "/.env", "ua": "Mozilla/5.0"}, "block"),
    ("扫描器 UA", {"path": "/", "ua": "sqlmap/1.7.2#stable"}, "block"),
    ("空 UA", {"path": "/", "ua": ""}, "challenge"),
    ("正常爬虫 UA", {"path": "/", "ua": "Bytespider"}, "challenge"),
]


def call(case: dict) -> str:
    body = json.dumps({"ip": "203.0.113.9", "method": "GET", **case}).encode()
    req = urllib.request.Request(
        URL + "/api/v1/waf/inspect",
        data=body,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {TOKEN}"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read())
    return data.get("verdict", "?")


def main() -> int:
    if not TOKEN:
        print("先 export TOKEN=登录拿到的 access_token")
        return 2
    bad = 0
    for name, case, expect in CASES:
        try:
            got = call(case)
        except urllib.error.HTTPError as exc:
            print(f"{name:12s} -> HTTP {exc.code}（token 过期？）")
            return 1
        ok = "✅" if got == expect else "❌"
        if got != expect:
            bad += 1
        print(f"{ok} {name:12s} 期望 {expect:9s} 实际 {got}")
    print(f"\n{len(CASES) - bad}/{len(CASES)} 通过")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
