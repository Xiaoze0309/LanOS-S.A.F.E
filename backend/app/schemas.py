"""请求体模型：只做输入校验，响应直接用 dict（字段少，避免重复维护）。"""

from __future__ import annotations

import re
from typing import Optional

from pydantic import BaseModel, Field, field_validator

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]{2,}$")
DOMAIN_RE = re.compile(r"^(?!-)[a-z0-9-]{1,63}(\.[a-z0-9-]{1,63})+$", re.IGNORECASE)


class RegisterIn(BaseModel):
    email: str = Field(max_length=255)
    password: str = Field(min_length=8, max_length=128)

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        v = v.strip().lower()
        if not EMAIL_RE.match(v):
            raise ValueError("邮箱格式不正确")
        return v


class RegisterVerifyIn(BaseModel):
    email: str
    code: str = Field(min_length=4, max_length=8)

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        return v.strip().lower()


class LoginIn(BaseModel):
    email: str
    password: str

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        return v.strip().lower()


class MfaIn(BaseModel):
    mfa_token: str
    code: str = Field(min_length=4, max_length=12)


class MfaTokenIn(BaseModel):
    mfa_token: str


class RefreshIn(BaseModel):
    refresh_token: str


class CodeIn(BaseModel):
    code: str = Field(min_length=4, max_length=12)


class PasswordIn(BaseModel):
    password: str


class DomainIn(BaseModel):
    domain: str = Field(max_length=255)

    @field_validator("domain")
    @classmethod
    def _domain(cls, v: str) -> str:
        v = v.strip().lower()
        for prefix in ("https://", "http://"):
            if v.startswith(prefix):
                v = v[len(prefix) :]
        v = v.split("/")[0]
        if v.startswith("www."):
            v = v[4:]
        if not DOMAIN_RE.match(v):
            raise ValueError("域名格式不正确，例如 example.com")
        return v


class WafRuleIn(BaseModel):
    name: str = Field(min_length=1, max_length=96)
    pattern: str = Field(min_length=1, max_length=2000)
    target: str = "path"
    action: str = "block"
    severity: str = "medium"

    @field_validator("target")
    @classmethod
    def _target(cls, v: str) -> str:
        if v not in {"path", "ua", "query", "body"}:
            raise ValueError("target 只能是 path/ua/query/body")
        return v

    @field_validator("action")
    @classmethod
    def _action(cls, v: str) -> str:
        if v not in {"block", "challenge", "log"}:
            raise ValueError("action 只能是 block/challenge/log")
        return v

    @field_validator("severity")
    @classmethod
    def _severity(cls, v: str) -> str:
        if v not in {"low", "medium", "high"}:
            raise ValueError("severity 只能是 low/medium/high")
        return v


class WafRulePatch(BaseModel):
    name: Optional[str] = None
    pattern: Optional[str] = None
    target: Optional[str] = None
    action: Optional[str] = None
    severity: Optional[str] = None
    enabled: Optional[bool] = None


class InspectIn(BaseModel):
    path: str = "/"
    ua: str = ""
    query: str = ""
    body: str = ""
    method: str = "GET"
    ip: str = "127.0.0.1"


class IngestIn(BaseModel):
    domain: str
    path: str = "/"
    method: str = "GET"
    ua: str = ""
    ip: str = ""
    status: int = 200
    bytes: int = 0


class ResetCodeIn(BaseModel):
    email: str

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        return v.strip().lower()


class ResetIn(BaseModel):
    email: str
    code: str = Field(min_length=4, max_length=8)
    new_password: str = Field(min_length=8, max_length=128)

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        return v.strip().lower()


class TrafficPointIn(BaseModel):
    t: int              # epoch 秒
    requests: int = 0
    bytes: int = 0
    blocked: int = 0


class TrafficAggregateIn(BaseModel):
    domain: str
    points: list[TrafficPointIn]
