from fastapi import APIRouter, Depends, Request, Response, status
from sqlalchemy.orm import Session

from app.api.deps import require_current_user
from app.core.config import settings
from app.db.session import get_db
from app.models.user import User
from app.schemas.auth import (
    BindPhoneRequest,
    AvatarUploadRequest,
    ChangePasswordRequest,
    DeleteAccountRequest,
    PasswordLoginRequest,
    ResetPasswordRequest,
    SmsLoginRequest,
    SmsSendCodeRequest,
    SmsSendCodeResponse,
    TokenResponse,
    UpdateProfileRequest,
    UsernameRegisterRequest,
)
from app.schemas.user import UserProfile
from app.services.auth_service import (
    issue_access_token,
    bind_phone,
    change_password,
    delete_account,
    login_or_register_by_phone,
    login_with_password,
    register_username,
    reset_password,
    send_sms_code,
    verify_sms_login_code,
    update_profile,
)
from app.services.avatar_service import save_avatar

router = APIRouter()


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def register(payload: UsernameRegisterRequest, db: Session = Depends(get_db)) -> TokenResponse:
    user = register_username(db, payload.username, payload.password, payload.nickname)
    return TokenResponse(access_token=issue_access_token(user.id), user=UserProfile.model_validate(user))


@router.post("/password/login", response_model=TokenResponse)
def password_login(
    payload: PasswordLoginRequest,
    db: Session = Depends(get_db),
) -> TokenResponse:
    user = login_with_password(db, payload.identifier, payload.password)
    token = issue_access_token(user.id)
    return TokenResponse(access_token=token, user=UserProfile.model_validate(user))


@router.get("/me", response_model=UserProfile)
def me(current_user: User = Depends(require_current_user)) -> UserProfile:
    return UserProfile.model_validate(current_user)


@router.patch("/me", response_model=UserProfile)
def update_current_user_profile(
    payload: UpdateProfileRequest,
    current_user: User = Depends(require_current_user),
    db: Session = Depends(get_db),
) -> UserProfile:
    return UserProfile.model_validate(update_profile(db, current_user, payload.nickname))


@router.post("/avatar", response_model=UserProfile)
def upload_current_user_avatar(
    payload: AvatarUploadRequest,
    current_user: User = Depends(require_current_user),
    db: Session = Depends(get_db),
) -> UserProfile:
    return UserProfile.model_validate(
        save_avatar(db, current_user, payload.image_base64, payload.content_type)
    )


@router.post("/sms/send-code", response_model=SmsSendCodeResponse)
def send_sms_code(
    payload: SmsSendCodeRequest,
    request: Request,
    db: Session = Depends(get_db),
) -> SmsSendCodeResponse:
    request_ip = request.client.host if request.client else None
    normalized_phone = send_sms_code(db, payload.phone, payload.purpose, request_ip=request_ip)
    return SmsSendCodeResponse(
        phone=normalized_phone,
        expires_in_seconds=settings.sms_code_expire_minutes * 60,
    )


@router.post("/sms/login", response_model=TokenResponse)
def login_with_sms(payload: SmsLoginRequest, db: Session = Depends(get_db)) -> TokenResponse:
    verification = verify_sms_login_code(db, payload.phone, payload.code)
    user = login_or_register_by_phone(db, verification.phone)
    token = issue_access_token(user.id)
    return TokenResponse(access_token=token, user=UserProfile.model_validate(user))


@router.post("/phone/bind", response_model=UserProfile)
def bind_current_user_phone(
    payload: BindPhoneRequest,
    current_user: User = Depends(require_current_user),
    db: Session = Depends(get_db),
) -> UserProfile:
    return UserProfile.model_validate(bind_phone(db, current_user, payload.phone, payload.code))


@router.post("/password/change", status_code=status.HTTP_204_NO_CONTENT)
def change_current_user_password(
    payload: ChangePasswordRequest,
    current_user: User = Depends(require_current_user),
    db: Session = Depends(get_db),
) -> Response:
    change_password(db, current_user, payload.old_password, payload.new_password)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.delete("/account", status_code=status.HTTP_204_NO_CONTENT)
def delete_current_user_account(
    payload: DeleteAccountRequest,
    current_user: User = Depends(require_current_user),
    db: Session = Depends(get_db),
) -> Response:
    delete_account(db, current_user, payload.password)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/password/reset", status_code=status.HTTP_204_NO_CONTENT)
def reset_user_password(payload: ResetPasswordRequest, db: Session = Depends(get_db)) -> Response:
    reset_password(db, payload.phone, payload.code, payload.new_password)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
