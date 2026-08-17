from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.models.app_version import AppVersion
from app.schemas.app_version import LatestVersionResponse, AppVersionResponse

router = APIRouter()


@router.get("/latest", response_model=LatestVersionResponse)
def get_latest_version(
    version_code: int = Query(..., description="Client's current version code"),
    db: Session = Depends(get_db),
) -> LatestVersionResponse:
    latest = db.query(AppVersion).order_by(AppVersion.version_code.desc()).first()
    if latest and latest.version_code > version_code:
        return LatestVersionResponse(latest_version=AppVersionResponse.model_validate(latest))
    return LatestVersionResponse(latest_version=None)
