from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field


class AdminUserSummary(BaseModel):
    id: int
    username: Optional[str]
    phone: Optional[str]
    email: Optional[str]
    nickname: str
    avatar_url: Optional[str]
    is_active: bool
    is_admin: bool
    created_at: datetime
    membership_plan_code: Optional[str]
    membership_status: str
    membership_expire_at: Optional[datetime]


class AdminMembershipGrantRequest(BaseModel):
    days: int = Field(ge=1, le=3650)


class BackendAdminProfile(BaseModel):
    email: str
    nickname: str


class BackendAdminTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    admin: BackendAdminProfile


class AdminOrderSummary(BaseModel):
    order_no: str
    user_id: int
    user_email: Optional[str]
    amount: Decimal
    status: str
    plan_code: str
    payment_channel: str
    created_at: datetime
    paid_at: Optional[datetime]


class AdminDashboardStats(BaseModel):
    total_users: int
    total_orders: int
    paid_orders: int
    paid_revenue: Decimal
    active_memberships: int
    today_sms_sent: int
    today_sms_verified: int
    today_sms_failed: int
    today_active_users: int


class AdminSmsLogSummary(BaseModel):
    id: int
    phone: str
    purpose: str
    delivery_status: str
    delivery_message: Optional[str] = None
    request_ip: Optional[str] = None
    created_at: datetime
    consumed_at: Optional[datetime] = None
    failed_attempts: int


class AdminDashboardResponse(BaseModel):
    stats: AdminDashboardStats
    recent_users: list[AdminUserSummary]
    recent_orders: list[AdminOrderSummary]


class AdminSmsListResponse(BaseModel):
    items: list[AdminSmsLogSummary]
    total: int


class AdminMembershipPlanResponse(BaseModel):
    id: int
    plan_code: str
    title: str
    price: Decimal
    original_price: Optional[Decimal]
    duration_days: Optional[int]
    bottom_label: str
    tag_text: Optional[str]
    is_default: bool
    is_active: bool
    sort_order: int
    created_at: datetime
    updated_at: datetime


class AdminMembershipPlanUpdate(BaseModel):
    title: str
    price: Decimal
    original_price: Optional[Decimal] = None
    duration_days: Optional[int] = None
    bottom_label: str
    tag_text: Optional[str] = None
    is_default: bool
    is_active: bool
    sort_order: int


from datetime import date


class AdminActiveLogSummary(BaseModel):
    id: int
    user_id: int
    user_nickname: str
    user_phone: Optional[str] = None
    user_email: Optional[str] = None
    active_date: date
    created_at: datetime


class AdminActiveTrendSummary(BaseModel):
    active_date: date
    count: int


class AdminActiveLogListResponse(BaseModel):
    items: list[AdminActiveLogSummary]
    total: int

