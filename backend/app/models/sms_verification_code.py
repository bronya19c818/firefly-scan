from datetime import datetime

from sqlalchemy import DateTime, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db.session import Base
from app.core.time import utc_now


class SmsVerificationCode(Base):
    __tablename__ = "sms_verification_codes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    phone: Mapped[str] = mapped_column(String(32), index=True)
    purpose: Mapped[str] = mapped_column(String(32), default="login", index=True)
    code_hash: Mapped[str] = mapped_column(String(128))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    consumed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, index=True)
    delivery_status: Mapped[str] = mapped_column(String(32), default="pending")
    delivery_message: Mapped[str | None] = mapped_column(String(255), nullable=True)
    request_ip: Mapped[str | None] = mapped_column(String(64), nullable=True)
    failed_attempts: Mapped[int] = mapped_column(Integer, default=0)
    template_snapshot: Mapped[str | None] = mapped_column(String(255), nullable=True)
