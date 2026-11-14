from datetime import datetime

from pydantic import BaseModel, ConfigDict


class SiteCreate(BaseModel):
    domain: str


class SiteOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    domain: str
    is_active: bool
    created_at: datetime
