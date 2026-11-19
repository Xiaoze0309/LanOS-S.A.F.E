from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """全部配置都能用环境变量或 backend/.env 覆盖，改完重启 uvicorn 生效。"""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # --- 数据库：Debian 13 里装的是 MariaDB 11.8，协议与 MySQL 一致 ---
    MYSQL_URL: str = "mysql+asyncmy://root:@127.0.0.1:3306/lanos_safe"

    # --- 令牌 ---
    SECRET_KEY: str = "dev-secret-please-change"
    ACCESS_TOKEN_TTL: int = 3600          # 访问令牌 1 小时
    REFRESH_TOKEN_TTL: int = 30 * 86400   # 刷新令牌 30 天
    MFA_TOKEN_TTL: int = 300              # 二次验证中间令牌 5 分钟

    # --- 邮箱验证码 ---
    EMAIL_CODE_TTL: int = 600
    SMTP_HOST: str = ""
    SMTP_PORT: int = 465
    SMTP_USER: str = ""
    SMTP_PASSWORD: str = ""
    SMTP_FROM: str = "no-reply@lanos.local"
    SMTP_SECURITY: str = "ssl"   # ssl | starttls | none

    # Microsoft 登录（OAuth2 设备码流程，只需 client_id，不需要 client_secret）
    MS_CLIENT_ID: str = ""
    MS_TENANT: str = "common"

    # 开发模式：注册/登录验证码直接通过接口返回（dev_code），方便没有邮箱时自测
    DEV_MODE: bool = True

    # 日志上报接口的共享密钥（X-Ingest-Key）
    INGEST_KEY: str = "change-me-ingest-key"

    # 允许的跨域来源，逗号分隔
    CORS_ORIGINS: str = "*"

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.CORS_ORIGINS.split(",") if o.strip()]


settings = Settings()
