from __future__ import annotations

import hashlib
import hmac
import secrets
import re
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import create_access_token, hash_password, verify_password
from app.models.sms_verification_code import SmsVerificationCode
from app.models.user import User
from app.services.tencent_sms_service import send_login_code_sms
from app.services.avatar_service import delete_local_avatar


def create_user(db: Session, email: str, password: str, nickname: str) -> User:
    user = User(email=email, nickname=nickname, password_hash=hash_password(password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def verify_user_credentials(db: Session, email: str, password: str) -> Optional[User]:
    user = db.query(User).filter(User.email == email).first()
    if user is None or not verify_password(password, user.password_hash):
        return None
    return user


SMS_PURPOSES = {"login", "bind_phone", "reset_password"}


def send_sms_code(
    db: Session,
    phone: str,
    purpose: str,
    request_ip: str | None = None,
) -> str:
    if purpose not in SMS_PURPOSES:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="不支持的验证码用途")
    normalized_phone = normalize_mainland_china_phone(phone)
    existing_user = db.query(User).filter(User.phone == normalized_phone).first()
    if purpose in {"login", "reset_password"} and existing_user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="该手机号尚未绑定账号")
    if purpose == "bind_phone" and existing_user is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="该手机号已被其他账号绑定")
    latest = (
        db.query(SmsVerificationCode)
        .filter(
            SmsVerificationCode.phone == normalized_phone,
            SmsVerificationCode.purpose == purpose,
        )
        .order_by(SmsVerificationCode.created_at.desc())
        .first()
    )
    now = datetime.now(timezone.utc)
    if latest is not None:
        last_created_at = _as_utc(latest.created_at)
        retry_after = last_created_at + timedelta(seconds=settings.sms_code_resend_interval_seconds)
        if retry_after > now:
            seconds = max(1, int((retry_after - now).total_seconds()))
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"验证码发送过于频繁，请在 {seconds} 秒后重试",
            )

    code = f"{secrets.randbelow(1_000_000):06d}"
    record = SmsVerificationCode(
        phone=normalized_phone,
        purpose=purpose,
        code_hash=_hash_sms_code(normalized_phone, code),
        expires_at=now + timedelta(minutes=settings.sms_code_expire_minutes),
        delivery_status="pending",
        request_ip=request_ip,
        template_snapshot=settings.tencent_sms_template_params,
    )
    db.add(record)
    db.flush()
    try:
        send_login_code_sms(normalized_phone, code, settings.sms_code_expire_minutes)
        record.delivery_status = "sent"
        record.delivery_message = "ok"
        db.add(record)
        db.commit()
    except Exception as e:
        record.delivery_status = "failed"
        if isinstance(e, HTTPException):
            record.delivery_message = str(e.detail)
        else:
            record.delivery_message = str(e)[:255]
        db.add(record)
        db.commit()
        raise e
    return normalized_phone


def send_login_sms_code(db: Session, phone: str, request_ip: str | None = None) -> str:
    return send_sms_code(db, phone, "login", request_ip)


def verify_sms_code(db: Session, phone: str, code: str, purpose: str) -> SmsVerificationCode:
    normalized_phone = normalize_mainland_china_phone(phone)
    now = datetime.now(timezone.utc)
    verification = (
        db.query(SmsVerificationCode)
        .filter(
            SmsVerificationCode.phone == normalized_phone,
            SmsVerificationCode.purpose == purpose,
        )
        .order_by(SmsVerificationCode.created_at.desc())
        .first()
    )
    if verification is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="请先获取验证码")
    if verification.consumed_at is not None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="验证码已使用，请重新获取")
    if _as_utc(verification.expires_at) < now:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="验证码已过期，请重新获取")
    if verification.failed_attempts >= settings.sms_code_max_attempts:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="验证码错误次数过多，请重新获取",
        )
    if not hmac.compare_digest(verification.code_hash, _hash_sms_code(normalized_phone, code)):
        verification.failed_attempts += 1
        db.add(verification)
        db.commit()
        if verification.failed_attempts >= settings.sms_code_max_attempts:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="验证码错误次数过多，请重新获取",
            )
        remaining = settings.sms_code_max_attempts - verification.failed_attempts
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"验证码错误，还可尝试 {remaining} 次",
        )

    verification.consumed_at = now
    db.add(verification)
    db.commit()
    db.refresh(verification)
    return verification


def verify_sms_login_code(db: Session, phone: str, code: str) -> SmsVerificationCode:
    return verify_sms_code(db, phone, code, "login")


def login_or_register_by_phone(db: Session, phone: str) -> User:
    normalized_phone = normalize_mainland_china_phone(phone)
    user = db.query(User).filter(User.phone == normalized_phone).first()
    if user is not None:
        if not user.is_active:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="账号已停用")
        return user

    raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="该手机号尚未绑定账号")


def register_username(db: Session, username: str, password: str, nickname: str | None) -> User:
    normalized = normalize_username(username)
    if db.query(User).filter(User.username == normalized).first() is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="用户名已存在")
    user = User(
        username=normalized,
        email=None,
        nickname=(nickname or normalized).strip(),
        password_hash=hash_password(password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def login_with_password(db: Session, identifier: str, password: str) -> User:
    raw = identifier.strip().lower()
    user = db.query(User).filter(User.username == raw).first()
    if user is None:
        try:
            phone = normalize_mainland_china_phone(raw)
        except HTTPException:
            phone = None
        if phone:
            user = db.query(User).filter(User.phone == phone).first()
    if user is None or not verify_password(password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名、手机号或密码错误")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="账号已停用")
    return user


def bind_phone(db: Session, user: User, phone: str, code: str) -> User:
    if user.phone:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="账号已经绑定手机号，暂不支持自行换绑")
    verification = verify_sms_code(db, phone, code, "bind_phone")
    normalized = verification.phone
    if db.query(User).filter(User.phone == normalized, User.id != user.id).first() is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="该手机号已被其他账号绑定")
    user.phone = normalized
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def update_profile(db: Session, user: User, nickname: str) -> User:
    normalized_nickname = nickname.strip()
    if not normalized_nickname:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="昵称不能为空",
        )
    user.nickname = normalized_nickname
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def change_password(db: Session, user: User, old_password: str, new_password: str) -> None:
    if not verify_password(old_password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="原密码错误")
    user.password_hash = hash_password(new_password)
    db.add(user)
    db.commit()


def delete_account(db: Session, user: User, password: str) -> None:
    if not verify_password(password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="密码错误")
    if user.phone:
        db.query(SmsVerificationCode).filter(SmsVerificationCode.phone == user.phone).delete(
            synchronize_session=False
        )
    old_avatar_url = user.avatar_url
    user.username = None
    user.phone = None
    user.email = None
    user.nickname = "已注销用户"
    user.avatar_url = None
    user.password_hash = hash_password(secrets.token_urlsafe(32))
    user.is_active = False
    db.add(user)
    db.commit()
    delete_local_avatar(old_avatar_url)


def reset_password(db: Session, phone: str, code: str, new_password: str) -> None:
    verification = verify_sms_code(db, phone, code, "reset_password")
    user = db.query(User).filter(User.phone == verification.phone).first()
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="该手机号尚未绑定账号")
    user.password_hash = hash_password(new_password)
    db.add(user)
    db.commit()


def normalize_username(username: str) -> str:
    value = username.strip().lower()
    if not re.fullmatch(r"[a-z0-9_]{4,32}", value):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="用户名须为 4-32 位小写字母、数字或下划线",
        )
    return value


def normalize_mainland_china_phone(phone: str) -> str:
    digits = "".join(ch for ch in phone if ch.isdigit())
    if digits.startswith("86") and len(digits) == 13:
        digits = digits[2:]
    if len(digits) != 11 or not digits.startswith("1"):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="请输入正确的中国大陆手机号")
    return digits


def issue_access_token(user_id: int) -> str:
    return create_access_token(str(user_id))


def _hash_sms_code(phone: str, code: str) -> str:
    secret = settings.secret_key or "change-me-in-production"
    return hmac.new(
        key=secret.encode("utf-8"),
        msg=f"{phone}:{code}".encode("utf-8"),
        digestmod=hashlib.sha256,
    ).hexdigest()


def _as_utc(value: datetime) -> datetime:
    return value if value.tzinfo is not None else value.replace(tzinfo=timezone.utc)
