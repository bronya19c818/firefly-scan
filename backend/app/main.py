from __future__ import annotations

from pathlib import Path
import logging
import re
from time import perf_counter
from typing import Dict
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from app.api.router import api_router
from app.core.config import settings
from app.core.logging import configure_logging
from app.core.request_context import reset_request_id, set_request_id
from app.db.bootstrap import ensure_database_ready
from app.db.session import Base, engine
from app.models import feedback, membership, membership_plan, order, sms_verification_code, user  # noqa: F401
from app.services.avatar_service import get_avatar_storage_dir

BASE_DIR = Path(__file__).resolve().parent
ADMIN_UI_DIR = BASE_DIR / "admin_ui"
PUBLIC_UI_DIR = BASE_DIR / "public_ui"
AVATAR_STORAGE_DIR = get_avatar_storage_dir()
AVATAR_STORAGE_DIR.mkdir(parents=True, exist_ok=True)

configure_logging()
logger = logging.getLogger(__name__)

REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,64}$")

if not all(
    (
        settings.alipay_app_id,
        settings.alipay_notify_url,
        settings.alipay_app_private_key,
        settings.alipay_alipay_public_key,
        settings.alipay_seller_id,
    )
):
    logger.warning("alipay_not_configured payment_order_creation_will_be_unavailable")

Base.metadata.create_all(bind=engine)
ensure_database_ready()

app = FastAPI(title=settings.app_name)
app.include_router(api_router, prefix="/api/v1")
app.mount("/assets", StaticFiles(directory=ADMIN_UI_DIR / "assets"), name="admin-assets")
app.mount("/public/assets", StaticFiles(directory=PUBLIC_UI_DIR / "assets"), name="public-assets")
app.mount("/uploads/avatars", StaticFiles(directory=AVATAR_STORAGE_DIR), name="user-avatars")


@app.middleware("http")
async def log_requests(request: Request, call_next):
    started_at = perf_counter()
    client = request.client.host if request.client else "unknown"
    supplied_request_id = request.headers.get("X-Request-ID", "").strip()
    request_id = (
        supplied_request_id
        if REQUEST_ID_PATTERN.fullmatch(supplied_request_id)
        else uuid4().hex
    )
    request_id_token = set_request_id(request_id)
    try:
        try:
            response = await call_next(request)
        except Exception:
            logger.exception(
                "request_failed method=%s path=%s client=%s duration_ms=%.2f",
                request.method,
                request.url.path,
                client,
                (perf_counter() - started_at) * 1000,
            )
            raise
        response.headers["X-Request-ID"] = request_id
        logger.info(
            "request_completed method=%s path=%s status=%s client=%s duration_ms=%.2f",
            request.method,
            request.url.path,
            response.status_code,
            client,
            (perf_counter() - started_at) * 1000,
        )
        return response
    finally:
        reset_request_id(request_id_token)


@app.get("/", include_in_schema=False)
def public_index() -> FileResponse:
    return FileResponse(PUBLIC_UI_DIR / "index.html")


@app.get("/privacy", include_in_schema=False)
def public_privacy() -> FileResponse:
    return FileResponse(PUBLIC_UI_DIR / "privacy.html")


@app.get("/terms", include_in_schema=False)
def public_terms() -> FileResponse:
    return FileResponse(PUBLIC_UI_DIR / "terms.html")


@app.get("/member-service-agreement", include_in_schema=False)
def public_member_service_agreement() -> FileResponse:
    return FileResponse(PUBLIC_UI_DIR / "member-service-agreement.html")


@app.get("/admin", include_in_schema=False)
def admin_index() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "index.html")


@app.get("/admin/users", include_in_schema=False)
def admin_users() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "users.html")


@app.get("/admin/active", include_in_schema=False)
def admin_active() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "active.html")



@app.get("/admin/orders", include_in_schema=False)
def admin_orders() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "orders.html")


@app.get("/admin/feedbacks", include_in_schema=False)
def admin_feedbacks() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "feedbacks.html")


@app.get("/admin/sms", include_in_schema=False)
def admin_sms() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "sms.html")


@app.get("/admin/versions", include_in_schema=False)
def admin_versions() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "versions.html")


@app.get("/admin/plans", include_in_schema=False)
def admin_plans() -> FileResponse:
    return FileResponse(ADMIN_UI_DIR / "plans.html")



@app.get("/health", tags=["system"])
def health_check() -> Dict[str, str]:
    return {"status": "ok"}
