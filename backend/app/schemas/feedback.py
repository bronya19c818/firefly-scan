from datetime import datetime

from pydantic import BaseModel, ConfigDict


class FeedbackCreate(BaseModel):
    content: str
    contact: str | None = None


class FeedbackResponse(BaseModel):
    id: int
    content: str
    contact: str | None = None
    status: str
    user_id: int | None = None
    user_nickname: str | None = None
    user_phone: str | None = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
