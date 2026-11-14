from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """全局配置：优先从环境变量或 .env 文件读取。"""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    MYSQL_URL: str = "mysql+asyncmy://root:root@127.0.0.1:3306/lanos"
    REDIS_URL: str = "redis://127.0.0.1:6379/0"
    SECRET_KEY: str = "change-me-in-production"


settings = Settings()
