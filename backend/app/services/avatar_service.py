from __future__ import annotations

import base64
import binascii
from pathlib import Path
from uuid import uuid4

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import settings
from app.models.user import User


CONTENT_TYPES = {
    "image/jpeg": ("jpg", lambda data: data.startswith(b"\xff\xd8\xff")),
    "image/png": ("png", lambda data: data.startswith(b"\x89PNG\r\n\x1a\n")),
    "image/webp": (
        "webp",
        lambda data: len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP",
    ),
}
BACKEND_DIR = Path(__file__).resolve().parents[2]


def get_avatar_storage_dir() -> Path:
    configured = Path(settings.avatar_storage_dir).expanduser()
    return configured.resolve() if configured.is_absolute() else (BACKEND_DIR / configured).resolve()


def save_avatar(db: Session, user: User, image_base64: str, content_type: str) -> User:
    try:
        image_bytes = base64.b64decode(image_base64, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="头像数据无效",
        )

    if not image_bytes or len(image_bytes) > settings.avatar_max_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="头像不能超过 5 MB",
        )

    image_format = CONTENT_TYPES.get(content_type)
    if image_format is None or not image_format[1](image_bytes):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="仅支持 JPG、PNG 或 WebP 图片",
        )

    storage_dir = get_avatar_storage_dir()
    storage_dir.mkdir(parents=True, exist_ok=True)
    filename = f"user-{user.id}-{uuid4().hex}.{image_format[0]}"
    target = storage_dir / filename
    temporary = storage_dir / f".{filename}.tmp"
    temporary.write_bytes(image_bytes)
    temporary.replace(target)

    old_avatar_url = user.avatar_url
    user.avatar_url = f"/uploads/avatars/{filename}"
    try:
        db.add(user)
        db.commit()
        db.refresh(user)
    except Exception:
        db.rollback()
        target.unlink(missing_ok=True)
        raise

    _delete_local_avatar(old_avatar_url, storage_dir)
    return user


def delete_local_avatar(avatar_url: str | None) -> None:
    _delete_local_avatar(
        avatar_url,
        get_avatar_storage_dir(),
    )


def _delete_local_avatar(avatar_url: str | None, storage_dir: Path) -> None:
    prefix = "/uploads/avatars/"
    if not avatar_url or not avatar_url.startswith(prefix):
        return
    filename = avatar_url.removeprefix(prefix)
    if not filename or Path(filename).name != filename:
        return
    (storage_dir / filename).unlink(missing_ok=True)
