from fastapi import APIRouter

from app.api.v1.admin import router as admin_router
from app.api.v1.admin_auth import router as admin_auth_router
from app.api.v1.app_version import router as app_version_router
from app.api.v1.auth import router as auth_router
from app.api.v1.feedback import router as feedback_router
from app.api.v1.membership import router as membership_router
from app.api.v1.payments import router as payments_router

api_router = APIRouter()
api_router.include_router(admin_router, prefix="/admin", tags=["admin"])
api_router.include_router(admin_auth_router, prefix="/admin/auth", tags=["admin-auth"])
api_router.include_router(app_version_router, prefix="/app/version", tags=["app-version"])
api_router.include_router(auth_router, prefix="/auth", tags=["auth"])
api_router.include_router(feedback_router, prefix="/feedback", tags=["feedback"])
api_router.include_router(membership_router, prefix="/membership", tags=["membership"])
api_router.include_router(payments_router, prefix="/payments", tags=["payments"])

