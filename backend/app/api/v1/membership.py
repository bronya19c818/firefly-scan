from typing import Optional

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import optional_current_user, require_current_user
from app.db.session import get_db
from app.models.user import User
from app.schemas.membership import MembershipPlanListResponse, MembershipStatus
from app.services.membership_service import build_membership_status
from app.services.payment_service import build_membership_plans_for_user

router = APIRouter()


@router.get("/current", response_model=MembershipStatus)
def current_membership(current_user: User = Depends(require_current_user)) -> MembershipStatus:
    return build_membership_status(current_user)


@router.get("/plans", response_model=MembershipPlanListResponse)
def membership_plans(
    current_user: Optional[User] = Depends(optional_current_user),
    db: Session = Depends(get_db),
) -> MembershipPlanListResponse:
    return build_membership_plans_for_user(db, current_user)
