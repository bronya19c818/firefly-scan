from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.api.deps import optional_current_user
from app.db.session import get_db
from app.models.feedback import Feedback
from app.models.user import User
from app.schemas.feedback import FeedbackCreate, FeedbackResponse

router = APIRouter()


@router.post("", response_model=FeedbackResponse, status_code=status.HTTP_201_CREATED)
def create_feedback(
    payload: FeedbackCreate,
    current_user: Optional[User] = Depends(optional_current_user),
    db: Session = Depends(get_db),
) -> FeedbackResponse:
    content_str = payload.content.strip()
    if not content_str:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="反馈内容不能为空")

    feedback = Feedback(
        content=content_str,
        contact=payload.contact.strip() if payload.contact else None,
        status="pending",
        user_id=current_user.id if current_user else None,
    )
    db.add(feedback)
    db.commit()
    db.refresh(feedback)

    # Convert to response
    res = FeedbackResponse(
        id=feedback.id,
        content=feedback.content,
        contact=feedback.contact,
        status=feedback.status,
        user_id=feedback.user_id,
        user_nickname=current_user.nickname if current_user else None,
        user_phone=current_user.phone if current_user else None,
        created_at=feedback.created_at,
    )
    return res
