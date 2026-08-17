from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field


class CreateOrderRequest(BaseModel):
    plan_code: str = Field(pattern="^(daily|monthly|quarterly|annual|lifetime)$")


class OrderResponse(BaseModel):
    order_no: str
    status: str
    amount: Decimal
    plan_code: str
    payment_channel: str
    order_string: str


class OrderDetail(OrderResponse):
    created_at: datetime
    paid_at: Optional[datetime]
