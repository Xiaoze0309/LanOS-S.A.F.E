from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import settings

# 使用 asyncmy 驱动的异步引擎
engine = create_async_engine(settings.MYSQL_URL, echo=False, pool_pre_ping=True)

# 异步会话工厂
AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def get_db():
    """FastAPI 依赖：提供请求级异步会话。"""
    async with AsyncSessionLocal() as session:
        yield session
