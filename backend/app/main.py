from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.v1 import sites
from app.db.models import Base
from app.db.session import engine


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时自动建表
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield


app = FastAPI(title="LanOS S.A.F.E API", lifespan=lifespan)

app.include_router(sites.router, prefix="/api/v1")
