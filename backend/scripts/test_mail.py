"""上线前自检：确认 SMTP 能真发信。

用法（backend 目录下，先激活 venv）：
    python scripts/test_mail.py you@example.com
"""

from __future__ import annotations

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.config import settings  # noqa: E402
from app.core.mailer import MailError, send_code  # noqa: E402


async def main() -> int:
    target = sys.argv[1] if len(sys.argv) > 1 else (settings.SMTP_USER or "")
    if not target:
        print("用法: python scripts/test_mail.py you@example.com")
        return 2

    print(f"SMTP: {settings.SMTP_HOST}:{settings.SMTP_PORT}  security={settings.SMTP_SECURITY}")
    print(f"USER: {settings.SMTP_USER}  FROM: {settings.SMTP_FROM}")
    print(f"收件人: {target}")
    try:
        await send_code(target, "123456", "reset")
    except MailError as exc:
        print(f"❌ 发送失败：{exc}")
        print("常见原因：授权码错、From 与登录账号不一致、端口/安全模式不匹配（465=ssl，587=starttls）")
        return 1
    print("✅ 已发送，请查收（含垃圾箱）")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
