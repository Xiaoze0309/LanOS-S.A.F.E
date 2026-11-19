"""邮件发送：真发（SMTP）为默认路径，DEV_MODE 才允许“不配邮箱也能测”。

配置（backend/.env）：
    SMTP_HOST=smtp.qq.com
    SMTP_PORT=465
    SMTP_SECURITY=ssl          # ssl | starttls | none
    SMTP_USER=123456@qq.com
    SMTP_PASSWORD=授权码
    SMTP_FROM=123456@qq.com    # 多数服务商要求 From 与登录账号一致

发送失败会抛 MailError，由接口层决定是返回 502 还是（DEV_MODE 下）降级。
"""

from __future__ import annotations

import asyncio
import concurrent.futures
import logging
import smtplib
import ssl
from email.header import Header
from email.mime.text import MIMEText
from email.utils import formataddr

from app.core.config import settings

logger = logging.getLogger("lanos.mail")

# 专用线程池 + 并发上限：SMTP 是阻塞 IO，绝不能占用事件循环，也不能无限开线程。
# 4 个线程、最多 3 个并发发送，超出的请求排队，不会拖垮 uvicorn。
_SMTP_POOL = concurrent.futures.ThreadPoolExecutor(max_workers=4, thread_name_prefix="smtp")
_SMTP_SLOTS = asyncio.Semaphore(3)
_SEND_TIMEOUT = 25

PURPOSE_LABEL = {
    "register": "注册验证",
    "login": "登录二次验证",
    "reset": "重置密码",
}


class MailError(Exception):
    """SMTP 未配置或发送失败。"""


def _send_sync(to: str, subject: str, body: str) -> None:
    if not settings.SMTP_HOST:
        raise MailError("未配置 SMTP_HOST")

    msg = MIMEText(body, "plain", "utf-8")
    msg["Subject"] = Header(subject, "utf-8")
    msg["From"] = formataddr(("LanOS S.A.F.E", settings.SMTP_FROM or settings.SMTP_USER))
    msg["To"] = to

    security = (settings.SMTP_SECURITY or "ssl").lower()
    timeout = 15

    if security == "ssl":
        server: smtplib.SMTP = smtplib.SMTP_SSL(
            settings.SMTP_HOST, settings.SMTP_PORT, timeout=timeout,
            context=ssl.create_default_context(),
        )
    else:
        server = smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT, timeout=timeout)
        server.ehlo()
        if security == "starttls":
            server.starttls(context=ssl.create_default_context())
            server.ehlo()

    try:
        if settings.SMTP_USER:
            server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
        server.sendmail(
            settings.SMTP_FROM or settings.SMTP_USER,
            [to],
            msg.as_string(),
        )
    finally:
        try:
            server.quit()
        except Exception:  # noqa: BLE001 - 关闭失败不影响结果
            pass


async def send_code(to: str, code: str, purpose: str) -> None:
    label = PURPOSE_LABEL.get(purpose, "验证")
    minutes = max(1, settings.EMAIL_CODE_TTL // 60)
    body = (
        f"你的 LanOS S.A.F.E {label}验证码是：{code}\n\n"
        f"验证码 {minutes} 分钟内有效，请勿转发给他人。\n"
        "如果这不是你本人的操作，请忽略本邮件并尽快修改密码。\n"
    )
    if not settings.SMTP_HOST:
        raise MailError("SMTP 未配置")

    loop = asyncio.get_running_loop()
    try:
        async with _SMTP_SLOTS:
            await asyncio.wait_for(
                loop.run_in_executor(
                    _SMTP_POOL,
                    _send_sync,
                    to,
                    f"LanOS S.A.F.E {label}验证码：{code}",
                    body,
                ),
                timeout=_SEND_TIMEOUT,
            )
    except asyncio.TimeoutError as exc:
        raise MailError(f"SMTP 超时（>{_SEND_TIMEOUT}s）") from exc
    except MailError:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.error("发送邮件失败 %s: %s", to, exc)
        raise MailError(str(exc)) from exc

    logger.info("验证码邮件已发送 %s（%s）", to, purpose)
