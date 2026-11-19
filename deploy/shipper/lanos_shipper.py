"""把 Nginx 的 JSON access log 实时推到 LanOS 后端（/api/v1/ingest）。

用法：
    export LANOS_URL=http://127.0.0.1:8000
    export INGEST_KEY=change-me-ingest-key
    python3 lanos_shipper.py /var/log/nginx/lanos_access.log

无第三方依赖；断线/后端重启会自动重试，文件被 logrotate 切割也能跟上。
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request

URL = os.environ.get("LANOS_URL", "http://127.0.0.1:8000").rstrip("/") + "/api/v1/ingest"
KEY = os.environ.get("INGEST_KEY", "")
BATCH = int(os.environ.get("BATCH", "20"))


def post(events: list[dict]) -> bool:
    for e in events:
        req = urllib.request.Request(
            URL,
            data=json.dumps(e).encode(),
            headers={"Content-Type": "application/json", "X-Ingest-Key": KEY},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=8) as resp:
                resp.read()
        except urllib.error.HTTPError as exc:
            if exc.code in (400, 404, 401):
                print(f"[skip] {exc.code} {e.get('path')}", flush=True)
                continue
            return False
        except Exception as exc:  # noqa: BLE001
            print(f"[retry] {exc}", flush=True)
            return False
    return True


def follow(path: str):
    fh = open(path, "r", encoding="utf-8", errors="ignore")
    fh.seek(0, os.SEEK_END)
    inode = os.fstat(fh.fileno()).st_ino
    while True:
        line = fh.readline()
        if not line:
            time.sleep(0.3)
            try:
                if os.stat(path).st_ino != inode:  # logrotate
                    fh.close()
                    fh = open(path, "r", encoding="utf-8", errors="ignore")
                    inode = os.fstat(fh.fileno()).st_ino
            except FileNotFoundError:
                time.sleep(1)
            continue
        yield line


def main() -> None:
    path = sys.argv[1] if len(sys.argv) > 1 else "/var/log/nginx/lanos_access.log"
    print(f"shipping {path} -> {URL}", flush=True)
    batch: list[dict] = []
    for line in follow(path):
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            raw = json.loads(line)
        except json.JSONDecodeError:
            continue
        batch.append(
            {
                "domain": raw.get("domain", ""),
                "path": raw.get("path", "/"),
                "method": raw.get("method", "GET"),
                "ua": raw.get("ua", ""),
                "ip": raw.get("ip", ""),
                "status": int(raw.get("status", 200) or 200),
                "bytes": int(raw.get("bytes", 0) or 0),
            }
        )
        if len(batch) >= BATCH:
            if post(batch):
                batch.clear()
            else:
                time.sleep(3)


if __name__ == "__main__":
    main()
