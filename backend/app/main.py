from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.exc import OperationalError

from app.api.v1 import auth, domains, oauth, system
from app.api.v1 import integrations as integrations_api
from app.api.v1 import waf as waf_api
from app.core.config import settings
from app.db.models import Base
from app.db.seed import ensure_builtin_rules
from app.db.session import AsyncSessionLocal, engine
from app.services.cf_sync import cf_sync_loop

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("lanos")


async def _bootstrap_database() -> None:
    """建表 + 预置规则；数据库没起来时给出人话提示并重试，而不是直接抛 2003。"""
    for attempt in range(1, 11):
        try:
            async with engine.begin() as conn:
                await conn.run_sync(Base.metadata.create_all)
            async with AsyncSessionLocal() as db:
                created = await ensure_builtin_rules(db)
            if created:
                logger.info("已预置 %s 条内置 WAF 规则", created)
            logger.info("数据库就绪：%s", settings.MYSQL_URL.split("@")[-1])
            return
        except OperationalError as exc:
            logger.error(
                "连接数据库失败（第 %s/10 次）：%s\n"
                "  → 检查 MariaDB 是否在跑：mariadb-admin ping\n"
                "  → 检查库是否存在：mariadb -uroot -e 'CREATE DATABASE IF NOT EXISTS lanos_safe CHARACTER SET utf8mb4'",
                attempt,
                exc,
            )
            await asyncio.sleep(2)
    raise RuntimeError("数据库连接失败，已重试 10 次，请先启动 MariaDB")


@asynccontextmanager
async def lifespan(app: FastAPI):
    await _bootstrap_database()
    stop = asyncio.Event()
    sync_task = asyncio.create_task(cf_sync_loop(stop))
    try:
        yield
    finally:
        stop.set()
        sync_task.cancel()
        await engine.dispose()


app = FastAPI(
    title="LanOS S.A.F.E API",
    version="2.5.0",
    description="多站点流量与安全网关控制面：认证 / 2FA / 域名校验 / 流量统计 / WAF",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=settings.cors_origin_list != ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(ValueError)
async def value_error_handler(request: Request, exc: ValueError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"detail": str(exc)})


@app.get("/", include_in_schema=False)
async def root() -> dict:
    return {
        "service": "LanOS S.A.F.E",
        "version": app.version,
        "docs": "/docs",
        "health": "/api/v1/system/health",
    }


for module in (auth, oauth, domains, waf_api, system, integrations_api):
    app.include_router(module.router, prefix="/api/v1")
