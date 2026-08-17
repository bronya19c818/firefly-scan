from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import PlainTextResponse
from sqlalchemy.orm import Session

from app.api.deps import require_current_user
from app.db.session import get_db
from app.models.user import User
from app.schemas.payment import CreateOrderRequest, OrderDetail, OrderResponse
from app.services.payment_service import create_order_for_user, get_order_for_user, process_alipay_notify

router = APIRouter()


@router.post("/orders", response_model=OrderResponse, status_code=status.HTTP_201_CREATED)
def create_order(
    payload: CreateOrderRequest,
    current_user: User = Depends(require_current_user),
    db: Session = Depends(get_db),
) -> OrderResponse:
    return create_order_for_user(db, current_user, payload)


@router.get("/orders/{order_no}", response_model=OrderDetail)
def get_order(
    order_no: str,
    current_user: User = Depends(require_current_user),
    db: Session = Depends(get_db),
) -> OrderDetail:
    order = get_order_for_user(db, current_user.id, order_no)
    if order is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Order not found")
    return order


@router.post("/alipay/notify", response_class=PlainTextResponse, include_in_schema=False)
async def alipay_notify(request: Request, db: Session = Depends(get_db)) -> PlainTextResponse:
    form = await request.form()
    payload = {key: str(value) for key, value in form.items()}
    processed = process_alipay_notify(db, payload)
    return PlainTextResponse("success" if processed else "failure")
