from __future__ import annotations

from datetime import date, datetime, timezone, timedelta
from decimal import Decimal
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import func
from sqlalchemy.orm import Session, selectinload, joinedload

from app.api.deps import require_backend_admin
from app.core.time import as_utc, utc_now
from app.db.session import get_db
from app.models.app_version import AppVersion
from app.models.feedback import Feedback
from app.models.membership import Membership
from app.models.order import Order
from app.models.sms_verification_code import SmsVerificationCode
from app.models.user import User
from app.models.user_active_log import UserActiveLog
from app.schemas.admin import (
    AdminDashboardResponse,
    AdminDashboardStats,
    AdminMembershipGrantRequest,
    AdminOrderSummary,
    AdminUserSummary,
    AdminSmsLogSummary,
    AdminSmsListResponse,
    AdminMembershipPlanResponse,
    AdminMembershipPlanUpdate,
    AdminActiveLogSummary,
    AdminActiveTrendSummary,
    AdminActiveLogListResponse,
)
from app.models.membership_plan import MembershipPlan
from app.schemas.app_version import AppVersionResponse, AppVersionCreate
from app.schemas.feedback import FeedbackResponse


router = APIRouter()


@router.get("/dashboard", response_model=AdminDashboardResponse)
def get_dashboard(
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> AdminDashboardResponse:
    total_users = db.query(func.count(User.id)).scalar() or 0
    total_orders = db.query(func.count(Order.id)).scalar() or 0
    paid_orders = db.query(func.count(Order.id)).filter(Order.status == "paid").scalar() or 0
    paid_revenue = db.query(func.coalesce(func.sum(Order.amount), 0)).filter(Order.status == "paid").scalar() or 0

    now = utc_now()
    active_memberships = (
        db.query(func.count(Membership.id))
        .filter(Membership.status == "active")
        .filter((Membership.expire_at.is_(None)) | (Membership.expire_at > now))
        .scalar()
        or 0
    )

    recent_users = (
        db.query(User)
        .options(selectinload(User.memberships))
        .order_by(User.created_at.desc())
        .limit(8)
        .all()
    )
    recent_orders = (
        db.query(Order)
        .options(selectinload(Order.user))
        .order_by(Order.created_at.desc())
        .limit(8)
        .all()
    )

    # Calculate today's SMS stats in local time (UTC+8)
    local_tz = timezone(timedelta(hours=8))
    local_now = datetime.now(local_tz)
    local_today_start = local_now.replace(hour=0, minute=0, second=0, microsecond=0)
    today_start_utc = local_today_start.astimezone(timezone.utc)

    today_sms_sent = (
        db.query(func.count(SmsVerificationCode.id))
        .filter(SmsVerificationCode.created_at >= today_start_utc)
        .scalar()
        or 0
    )
    today_sms_verified = (
        db.query(func.count(SmsVerificationCode.id))
        .filter(SmsVerificationCode.created_at >= today_start_utc)
        .filter(SmsVerificationCode.consumed_at.isnot(None))
        .scalar()
        or 0
    )
    today_sms_failed = (
        db.query(func.count(SmsVerificationCode.id))
        .filter(SmsVerificationCode.created_at >= today_start_utc)
        .filter(SmsVerificationCode.delivery_status == "failed")
        .scalar()
        or 0
    )

    today_active_users = (
        db.query(func.count(UserActiveLog.user_id))
        .filter(UserActiveLog.active_date == date.today())
        .scalar()
        or 0
    )

    return AdminDashboardResponse(
        stats=AdminDashboardStats(
            total_users=total_users,
            total_orders=total_orders,
            paid_orders=paid_orders,
            paid_revenue=Decimal(str(paid_revenue)),
            active_memberships=active_memberships,
            today_sms_sent=today_sms_sent,
            today_sms_verified=today_sms_verified,
            today_sms_failed=today_sms_failed,
            today_active_users=today_active_users,
        ),
        recent_users=[_build_user_summary(user) for user in recent_users],
        recent_orders=[_build_order_summary(order) for order in recent_orders],
    )


@router.get("/sms", response_model=AdminSmsListResponse)
def list_sms_logs(
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    search: Optional[str] = Query(default=None),
    status: Optional[str] = Query(default=None),
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> AdminSmsListResponse:
    query = db.query(SmsVerificationCode).order_by(SmsVerificationCode.created_at.desc())

    if search:
        pattern = f"%{search.strip()}%"
        query = query.filter(
            SmsVerificationCode.phone.ilike(pattern) | SmsVerificationCode.request_ip.ilike(pattern)
        )

    if status:
        if status == "success":
            query = query.filter(SmsVerificationCode.delivery_status.in_(["sent", "success"]))
        elif status == "failed":
            query = query.filter(SmsVerificationCode.delivery_status == "failed")
        elif status == "pending":
            query = query.filter(SmsVerificationCode.delivery_status == "pending")

    total = query.count()
    items = query.offset(offset).limit(limit).all()

    return AdminSmsListResponse(
        items=[_build_sms_summary(sms) for sms in items],
        total=total,
    )


@router.get("/users", response_model=list[AdminUserSummary])
def list_users(
    limit: int = Query(default=50, ge=1, le=200),
    search: Optional[str] = Query(default=None),
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> list[AdminUserSummary]:
    query = (
        db.query(User)
        .options(selectinload(User.memberships))
        .order_by(User.created_at.desc())
    )
    if search:
        pattern = f"%{search.strip()}%"
        query = query.filter(
            User.username.ilike(pattern)
            | User.phone.ilike(pattern)
            | User.email.ilike(pattern)
            | User.nickname.ilike(pattern)
        )

    users = query.limit(limit).all()
    return [_build_user_summary(user) for user in users]


@router.post("/users/{user_id}/membership", response_model=AdminUserSummary)
def grant_or_extend_membership(
    user_id: int,
    payload: AdminMembershipGrantRequest,
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> AdminUserSummary:
    user = (
        db.query(User)
        .options(selectinload(User.memberships))
        .filter(User.id == user_id)
        .first()
    )
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户不存在")

    now = utc_now()
    active_memberships = [
        membership
        for membership in user.memberships
        if membership.status == "active" and (membership.expire_at is None or as_utc(membership.expire_at) > now)
    ]
    latest = max(active_memberships, key=lambda item: as_utc(item.started_at), default=None)

    if latest is not None and latest.expire_at is None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="永久会员无需延长")

    if latest is not None:
        latest.expire_at = as_utc(latest.expire_at) + timedelta(days=payload.days)
        db.add(latest)
    else:
        previous = max(user.memberships, key=lambda item: as_utc(item.started_at), default=None)
        db.add(
            Membership(
                user_id=user.id,
                plan_code=previous.plan_code if previous else "manual",
                status="active",
                source="manual",
                started_at=now,
                expire_at=now + timedelta(days=payload.days),
            )
        )

    db.commit()
    db.refresh(user)
    return _build_user_summary(user)


@router.delete("/users/{user_id}/membership", response_model=AdminUserSummary)
def terminate_membership(
    user_id: int,
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> AdminUserSummary:
    user = (
        db.query(User)
        .options(selectinload(User.memberships))
        .filter(User.id == user_id)
        .first()
    )
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="用户不存在")

    now = utc_now()
    active_memberships = [
        membership
        for membership in user.memberships
        if membership.status == "active" and (membership.expire_at is None or as_utc(membership.expire_at) > now)
    ]
    if not active_memberships:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="该用户当前不是有效会员")

    for membership in active_memberships:
        membership.status = "terminated"
        membership.expire_at = now
        db.add(membership)

    db.commit()
    db.refresh(user)
    return _build_user_summary(user)


@router.get("/orders", response_model=list[AdminOrderSummary])
def list_orders(
    limit: int = Query(default=50, ge=1, le=200),
    status: Optional[str] = Query(default=None),
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> list[AdminOrderSummary]:
    query = db.query(Order).options(selectinload(Order.user)).order_by(Order.created_at.desc())
    if status:
        query = query.filter(Order.status == status)

    orders = query.limit(limit).all()
    return [_build_order_summary(order) for order in orders]


def _build_user_summary(user: User) -> AdminUserSummary:
    now = utc_now()
    active_memberships = [
        membership
        for membership in user.memberships
        if membership.status == "active" and (membership.expire_at is None or as_utc(membership.expire_at) > now)
    ]
    latest = max(active_memberships, key=lambda item: as_utc(item.started_at), default=None)
    return AdminUserSummary(
        id=user.id,
        username=user.username,
        phone=user.phone,
        email=user.email,
        nickname=user.nickname,
        avatar_url=user.avatar_url,
        is_active=user.is_active,
        is_admin=user.is_admin,
        created_at=user.created_at,
        membership_plan_code=latest.plan_code if latest else None,
        membership_status=latest.status if latest else "inactive",
        membership_expire_at=latest.expire_at if latest else None,
    )


def _build_order_summary(order: Order) -> AdminOrderSummary:
    return AdminOrderSummary(
        order_no=order.order_no,
        user_id=order.user_id,
        user_email=order.user.email,
        amount=Decimal(str(order.amount)),
        status=order.status,
        plan_code=order.plan_code,
        payment_channel=order.payment_channel,
        created_at=order.created_at,
        paid_at=order.paid_at,
    )


def _build_sms_summary(sms: SmsVerificationCode) -> AdminSmsLogSummary:
    return AdminSmsLogSummary(
        id=sms.id,
        phone=sms.phone,
        purpose=sms.purpose,
        delivery_status=sms.delivery_status,
        delivery_message=sms.delivery_message,
        request_ip=sms.request_ip,
        created_at=sms.created_at,
        consumed_at=sms.consumed_at,
        failed_attempts=sms.failed_attempts,
    )


@router.get("/feedbacks", response_model=list[FeedbackResponse])
def list_feedbacks(
    limit: int = Query(default=50, ge=1, le=200),
    status: Optional[str] = Query(default=None),
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> list[FeedbackResponse]:
    query = db.query(Feedback).options(selectinload(Feedback.user)).order_by(Feedback.created_at.desc())
    if status:
        query = query.filter(Feedback.status == status)
    feedbacks = query.limit(limit).all()

    result = []
    for f in feedbacks:
        result.append(
            FeedbackResponse(
                id=f.id,
                content=f.content,
                contact=f.contact,
                status=f.status,
                user_id=f.user_id,
                user_nickname=f.user.nickname if f.user else None,
                user_phone=f.user.phone if f.user else None,
                created_at=f.created_at,
            )
        )
    return result


@router.post("/feedbacks/{feedback_id}/resolve", response_model=FeedbackResponse)
def resolve_feedback(
    feedback_id: int,
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> FeedbackResponse:
    feedback = db.query(Feedback).options(selectinload(Feedback.user)).filter(Feedback.id == feedback_id).first()
    if not feedback:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="反馈工单不存在")
    feedback.status = "resolved"
    db.add(feedback)
    db.commit()
    db.refresh(feedback)
    return FeedbackResponse(
        id=feedback.id,
        content=feedback.content,
        contact=feedback.contact,
        status=feedback.status,
        user_id=feedback.user_id,
        user_nickname=feedback.user.nickname if feedback.user else None,
        user_phone=feedback.user.phone if feedback.user else None,
        created_at=feedback.created_at,
    )


@router.post("/app-versions", response_model=AppVersionResponse)
def create_app_version(
    payload: AppVersionCreate,
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> AppVersionResponse:
    existing = db.query(AppVersion).filter(AppVersion.version_code == payload.version_code).first()
    if existing:
        existing.version_name = payload.version_name.strip()
        existing.download_url = payload.download_url.strip()
        existing.update_log = payload.update_log.strip()
        existing.is_force_update = payload.is_force_update
        db.add(existing)
        db.commit()
        db.refresh(existing)
        return existing

    new_version = AppVersion(
        version_code=payload.version_code,
        version_name=payload.version_name.strip(),
        download_url=payload.download_url.strip(),
        update_log=payload.update_log.strip(),
        is_force_update=payload.is_force_update,
    )
    db.add(new_version)
    db.commit()
    db.refresh(new_version)
    return new_version


@router.get("/app-versions", response_model=list[AppVersionResponse])
def list_app_versions(
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> list[AppVersionResponse]:
    return db.query(AppVersion).order_by(AppVersion.version_code.desc()).all()


@router.delete("/app-versions/{version_code}", status_code=status.HTTP_204_NO_CONTENT)
def delete_app_version(
    version_code: int,
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> Response:
    version = db.query(AppVersion).filter(AppVersion.version_code == version_code).first()
    if not version:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="版本记录不存在")
    db.delete(version)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/membership-plans", response_model=list[AdminMembershipPlanResponse])
def list_membership_plans(
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> list[AdminMembershipPlanResponse]:
    return db.query(MembershipPlan).order_by(MembershipPlan.sort_order.asc(), MembershipPlan.id.asc()).all()


@router.put("/membership-plans/{plan_code}", response_model=AdminMembershipPlanResponse)
def update_membership_plan(
    plan_code: str,
    payload: AdminMembershipPlanUpdate,
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> AdminMembershipPlanResponse:
    plan = db.query(MembershipPlan).filter(MembershipPlan.plan_code == plan_code).first()
    if not plan:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="套餐未找到")

    plan.title = payload.title.strip()
    plan.price = payload.price
    plan.original_price = payload.original_price
    plan.duration_days = payload.duration_days
    plan.bottom_label = payload.bottom_label.strip()
    plan.tag_text = payload.tag_text.strip() if payload.tag_text else None
    plan.is_default = payload.is_default
    plan.is_active = payload.is_active
    plan.sort_order = payload.sort_order

    db.add(plan)
    db.commit()
    db.refresh(plan)
    return plan


@router.get("/active/trends", response_model=list[AdminActiveTrendSummary])
def get_active_trends(
    days: int = Query(default=30, ge=7, le=90),
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> list[AdminActiveTrendSummary]:
    from datetime import timedelta
    start_date = date.today() - timedelta(days=days)

    results = (
        db.query(
            UserActiveLog.active_date,
            func.count(UserActiveLog.user_id).label("count")
        )
        .filter(UserActiveLog.active_date >= start_date)
        .group_by(UserActiveLog.active_date)
        .order_by(UserActiveLog.active_date.asc())
        .all()
    )

    date_map = {r.active_date: r.count for r in results}
    trends = []
    for i in range(days + 1):
        d = start_date + timedelta(days=i)
        trends.append(
            AdminActiveTrendSummary(
                active_date=d,
                count=date_map.get(d, 0)
            )
        )
    return trends


@router.get("/active/logs", response_model=AdminActiveLogListResponse)
def list_active_logs(
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    search: Optional[str] = Query(default=None),
    active_date: Optional[date] = Query(default=None),
    _: str = Depends(require_backend_admin),
    db: Session = Depends(get_db),
) -> AdminActiveLogListResponse:
    query = db.query(UserActiveLog).options(joinedload(UserActiveLog.user))

    if active_date:
        query = query.filter(UserActiveLog.active_date == active_date)

    if search:
        pattern = f"%{search.strip()}%"
        query = query.join(User, UserActiveLog.user_id == User.id).filter(
            User.username.ilike(pattern)
            | User.phone.ilike(pattern)
            | User.email.ilike(pattern)
            | User.nickname.ilike(pattern)
        )

    query = query.order_by(UserActiveLog.created_at.desc())

    total = query.count()
    items = query.offset(offset).limit(limit).all()

    return AdminActiveLogListResponse(
        items=[
            AdminActiveLogSummary(
                id=log.id,
                user_id=log.user_id,
                user_nickname=log.user.nickname,
                user_phone=log.user.phone,
                user_email=log.user.email,
                active_date=log.active_date,
                created_at=log.created_at
            )
            for log in items
        ],
        total=total
    )
