from __future__ import annotations

from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import settings

_kwargs: dict = {"echo": False, "pool_pre_ping": True, "pool_recycle": 1800}
if not settings.MYSQL_URL.startswith("sqlite"):
    _kwargs.update(pool_size=5, max_overflow=10)

engine = create_async_engine(settings.MYSQL_URL, **_kwargs)

AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def get_db() -> AsyncIterator[AsyncSession]:
    async with AsyncSessionLocal() as session:
        yield session
