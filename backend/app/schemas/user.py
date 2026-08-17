from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr


class UserProfile(BaseModel):
    id: int
    username: str | None = None
    email: str | None = None
    nickname: str
    avatar_url: str | None = None
    phone: str | None = None
    is_active: bool
    is_admin: bool
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
