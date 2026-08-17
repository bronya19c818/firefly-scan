from __future__ import annotations

from datetime import datetime, timedelta
from decimal import Decimal, InvalidOperation
import logging
from typing import Optional
from uuid import uuid4

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.time import utc_now
from app.models.membership import Membership
from app.models.membership_plan import MembershipPlan as DBMembershipPlan
from app.models.order import Order
from app.models.user import User
from app.schemas.membership import MembershipPlan, MembershipPlanListResponse
from app.schemas.payment import CreateOrderRequest, OrderDetail, OrderResponse
from app.services.alipay_service import build_app_order_string, verify_notify_signature

logger = logging.getLogger("payment.audit")


def create_order_for_user(db: Session, user: User, payload: CreateOrderRequest) -> OrderResponse:
    plan = db.query(DBMembershipPlan).filter(
        DBMembershipPlan.plan_code == payload.plan_code,
        DBMembershipPlan.is_active == True,
    ).first()
    if plan is None:
        logger.warning("order_rejected user_id=%s plan_code=%s reason=unsupported_plan", user.id, payload.plan_code)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Unsupported plan code")
    amount = plan.price
    plan_title = plan.title
    order = Order(
        order_no=f"JZ{uuid4().hex[:18].upper()}",
        user_id=user.id,
        plan_code=payload.plan_code,
        amount=amount,
        payment_channel="alipay",
        status="pending",
    )
    try:
        order_string = build_app_order_string(
            order_no=order.order_no,
            amount=str(amount),
            subject=f"桔子扫描{plan_title}",
            body=f"购买{plan_title}",
        )
        db.add(order)
        db.commit()
        db.refresh(order)
    except ValueError as exc:
        db.rollback()
        logger.exception(
            "order_creation_failed user_id=%s plan_code=%s reason=alipay_configuration",
            user.id,
            payload.plan_code,
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="支付服务暂未配置完成，请稍后重试",
        ) from exc
    except Exception:
        db.rollback()
        logger.exception("order_creation_failed user_id=%s plan_code=%s", user.id, payload.plan_code)
        raise
    logger.info(
        "order_created order_no=%s user_id=%s plan_code=%s amount=%s",
        order.order_no,
        user.id,
        order.plan_code,
        order.amount,
    )
    return OrderResponse(
        order_no=order.order_no,
        status=order.status,
        amount=order.amount,
        plan_code=order.plan_code,
        payment_channel=order.payment_channel,
        order_string=order_string,
    )


def build_membership_plans_for_user(db: Session, user: Optional[User]) -> MembershipPlanListResponse:
    db_plans = (
        db.query(DBMembershipPlan)
        .filter(DBMembershipPlan.is_active == True)
        .order_by(DBMembershipPlan.sort_order.asc(), DBMembershipPlan.id.asc())
        .all()
    )
    plans = [
        MembershipPlan(
            plan_code=p.plan_code,
            title=p.title,
            price=p.price,
            original_price=p.original_price,
            bottom_label=p.bottom_label,
            tag_text=p.tag_text,
            is_default=p.is_default,
        )
        for p in db_plans
    ]
    return MembershipPlanListResponse(plans=plans, payment_channels=["alipay"])


def get_order_for_user(db: Session, user_id: int, order_no: str) -> Optional[OrderDetail]:
    order = db.query(Order).filter(Order.user_id == user_id, Order.order_no == order_no).first()
    if order is None:
        return None
    return OrderDetail(
        order_no=order.order_no,
        status=order.status,
        amount=order.amount,
        plan_code=order.plan_code,
        payment_channel=order.payment_channel,
        order_string="",
        created_at=order.created_at,
        paid_at=order.paid_at,
    )


def process_alipay_notify(db: Session, payload: dict[str, str]) -> bool:
    order_no = payload.get("out_trade_no")
    trade_status = payload.get("trade_status")
    alipay_trade_no = payload.get("trade_no")

    try:
        signature_valid = verify_notify_signature(payload)
    except (TypeError, ValueError):
        logger.exception("alipay_notify_rejected reason=invalid_verification_configuration order_no=%s", order_no)
        return False

    if not signature_valid:
        logger.warning("alipay_notify_rejected reason=invalid_signature order_no=%s", payload.get("out_trade_no"))
        return False

    if payload.get("app_id") != settings.alipay_app_id:
        logger.warning("alipay_notify_rejected reason=app_id_mismatch order_no=%s", order_no)
        return False
    if payload.get("seller_id") != settings.alipay_seller_id:
        logger.warning("alipay_notify_rejected reason=seller_id_mismatch order_no=%s", order_no)
        return False
    if not order_no or not alipay_trade_no or trade_status not in {"TRADE_SUCCESS", "TRADE_FINISHED"}:
        logger.warning("alipay_notify_rejected reason=invalid_status order_no=%s trade_status=%s", order_no, trade_status)
        return False

    try:
        order = (
            db.query(Order)
            .filter(Order.order_no == order_no)
            .with_for_update()
            .first()
        )
        if order is None:
            logger.warning("alipay_notify_rejected reason=order_not_found order_no=%s", order_no)
            db.rollback()
            return False

        try:
            notified_amount = Decimal(payload.get("total_amount", "")).quantize(Decimal("0.01"))
        except (InvalidOperation, TypeError):
            logger.warning("alipay_notify_rejected reason=invalid_amount order_no=%s", order_no)
            db.rollback()
            return False
        expected_amount = Decimal(str(order.amount)).quantize(Decimal("0.01"))
        if notified_amount != expected_amount:
            logger.warning(
                "alipay_notify_rejected reason=amount_mismatch order_no=%s expected_amount=%s notified_amount=%s",
                order_no,
                expected_amount,
                notified_amount,
            )
            db.rollback()
            return False

        if order.status == "paid":
            if order.alipay_trade_no != alipay_trade_no:
                logger.error(
                    "alipay_notify_rejected reason=trade_no_mismatch order_no=%s stored_trade_no=%s notified_trade_no=%s",
                    order_no,
                    order.alipay_trade_no,
                    alipay_trade_no,
                )
                db.rollback()
                return False
            db.commit()
            logger.info("alipay_notify_duplicate order_no=%s trade_no=%s", order_no, alipay_trade_no)
            return True

        if order.status != "pending":
            logger.warning("alipay_notify_rejected reason=invalid_order_state order_no=%s status=%s", order_no, order.status)
            db.rollback()
            return False

        now = utc_now()
        order.status = "paid"
        order.paid_at = now
        order.alipay_trade_no = alipay_trade_no
        db.add(order)
        _grant_membership(
            db,
            user_id=order.user_id,
            plan_code=order.plan_code,
            source="alipay",
            started_at=now,
        )
        db.commit()
    except Exception:
        db.rollback()
        logger.exception("alipay_notify_failed order_no=%s trade_no=%s", order_no, alipay_trade_no)
        return False

    logger.info(
        "alipay_notify_processed order_no=%s trade_no=%s trade_status=%s amount=%s",
        order_no,
        alipay_trade_no,
        trade_status,
        payload.get("total_amount"),
    )

    return True


def _grant_membership(
    db: Session,
    user_id: int,
    plan_code: str,
    source: str,
    started_at: datetime,
) -> None:
    plan = db.query(DBMembershipPlan).filter(DBMembershipPlan.plan_code == plan_code).first()
    if plan is None:
        logger.error("grant_membership_failed plan_code_not_found=%s user_id=%d", plan_code, user_id)
        raise ValueError(f"Membership plan not found: {plan_code}")

    duration_days = plan.duration_days
    expire_at = None if duration_days is None else started_at + timedelta(days=duration_days)

    membership = Membership(
        user_id=user_id,
        plan_code=plan_code,
        status="active",
        source=source,
        started_at=started_at,
        expire_at=expire_at,
    )
    db.add(membership)
