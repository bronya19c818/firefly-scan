from typing import Literal

from pydantic import BaseModel, EmailStr, Field

from app.schemas.user import UserProfile


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6, max_length=128)
    nickname: str = Field(min_length=1, max_length=64)


class LoginRequest(BaseModel):
    email: str
    password: str = Field(min_length=6, max_length=128)


class UsernameRegisterRequest(BaseModel):
    username: str = Field(min_length=4, max_length=32)
    password: str = Field(min_length=6, max_length=128)
    nickname: str | None = Field(default=None, min_length=1, max_length=64)


class PasswordLoginRequest(BaseModel):
    identifier: str = Field(min_length=4, max_length=64)
    password: str = Field(min_length=6, max_length=128)


class SmsSendCodeRequest(BaseModel):
    phone: str = Field(min_length=11, max_length=20)
    purpose: Literal["login", "bind_phone", "reset_password"] = "login"


class SmsSendCodeResponse(BaseModel):
    success: bool = True
    phone: str
    expires_in_seconds: int = 300


class SmsLoginRequest(BaseModel):
    phone: str = Field(min_length=11, max_length=20)
    code: str = Field(min_length=4, max_length=8)


class BindPhoneRequest(SmsLoginRequest):
    pass


class ChangePasswordRequest(BaseModel):
    old_password: str = Field(min_length=6, max_length=128)
    new_password: str = Field(min_length=6, max_length=128)


class UpdateProfileRequest(BaseModel):
    nickname: str = Field(min_length=1, max_length=64)


class AvatarUploadRequest(BaseModel):
    image_base64: str = Field(min_length=1, max_length=7_000_000)
    content_type: Literal["image/jpeg", "image/png", "image/webp"]


class DeleteAccountRequest(BaseModel):
    password: str = Field(min_length=6, max_length=128)


class ResetPasswordRequest(SmsLoginRequest):
    new_password: str = Field(min_length=6, max_length=128)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserProfile
