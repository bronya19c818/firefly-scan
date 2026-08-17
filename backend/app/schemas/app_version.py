from __future__ import annotations

from datetime import datetime
from pydantic import BaseModel, ConfigDict


class AppVersionResponse(BaseModel):
    version_code: int
    version_name: str
    download_url: str
    update_log: str
    is_force_update: bool
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class LatestVersionResponse(BaseModel):
    latest_version: AppVersionResponse | None = None


class AppVersionCreate(BaseModel):
    version_code: int
    version_name: str
    download_url: str
    update_log: str
    is_force_update: bool = False
