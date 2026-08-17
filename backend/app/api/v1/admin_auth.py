from hmac import compare_digest

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.deps import require_backend_admin
from app.core.config import settings
from app.core.security import create_access_token
from app.schemas.admin import BackendAdminProfile, BackendAdminTokenResponse
from app.schemas.auth import LoginRequest

router = APIRouter()


@router.post("/login", response_model=BackendAdminTokenResponse)
def admin_login(payload: LoginRequest) -> BackendAdminTokenResponse:
    if not settings.bootstrap_admin_email or not settings.bootstrap_admin_password:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Admin login is not configured")
    email_matches = compare_digest(payload.email.strip(), settings.bootstrap_admin_email.strip())
    password_matches = compare_digest(payload.password, settings.bootstrap_admin_password)
    if not email_matches or not password_matches:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")
    profile = _admin_profile()
    return BackendAdminTokenResponse(
        access_token=create_access_token("backend-admin", scope="admin"),
        admin=profile,
    )


@router.get("/me", response_model=BackendAdminProfile)
def admin_me(_: str = Depends(require_backend_admin)) -> BackendAdminProfile:
    return _admin_profile()


def _admin_profile() -> BackendAdminProfile:
    return BackendAdminProfile(
        email=settings.bootstrap_admin_email,
        nickname=settings.bootstrap_admin_nickname or "Admin",
    )
