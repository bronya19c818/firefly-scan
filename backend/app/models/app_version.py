from datetime import datetime

from sqlalchemy import Boolean, DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.time import utc_now
from app.db.session import Base


class AppVersion(Base):
    __tablename__ = "app_versions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    version_code: Mapped[int] = mapped_column(Integer, unique=True, index=True, nullable=False)
    version_name: Mapped[str] = mapped_column(String(32), nullable=False)
    download_url: Mapped[str] = mapped_column(String(255), nullable=False)
    update_log: Mapped[str] = mapped_column(Text, nullable=False)
    is_force_update: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
