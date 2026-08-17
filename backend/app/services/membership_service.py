from app.core.time import as_utc, utc_now
from app.models.user import User
from app.schemas.membership import MembershipStatus


def build_membership_status(user: User) -> MembershipStatus:
    now = utc_now()
    active_memberships = [
        item
        for item in user.memberships
        if item.status == "active" and (item.expire_at is None or as_utc(item.expire_at) > now)
    ]
    latest = max(active_memberships, key=lambda item: as_utc(item.started_at), default=None)
    return MembershipStatus(
        is_vip=latest is not None,
        plan_code=latest.plan_code if latest else None,
        status=latest.status if latest else "inactive",
        source=latest.source if latest else None,
        expire_at=latest.expire_at if latest else None,
    )
