from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel


class MembershipStatus(BaseModel):
    is_vip: bool
    plan_code: Optional[str]
    status: str
    source: Optional[str]
    expire_at: Optional[datetime]


class MembershipPlan(BaseModel):
    plan_code: str
    title: str
    price: Decimal
    original_price: Optional[Decimal]
    bottom_label: str
    tag_text: Optional[str]
    is_default: bool = False


class MembershipPlanListResponse(BaseModel):
    plans: list[MembershipPlan]
    payment_channels: list[str]
