from __future__ import annotations

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path
import sys

from app.core.config import settings
from app.core.request_context import get_request_id


class RequestContextFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = get_request_id()
        return True


def configure_logging() -> None:
    level = getattr(logging, settings.log_level.upper(), logging.INFO)
    formatter = logging.Formatter(
        "%(asctime)s %(levelname)s %(name)s request_id=%(request_id)s %(message)s"
    )
    context_filter = RequestContextFilter()
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)
    console_handler.addFilter(context_filter)

    log_dir = Path(settings.log_dir)
    if not log_dir.is_absolute():
        project_root = Path(__file__).resolve().parents[2]
        log_dir = project_root / log_dir
    log_dir.mkdir(parents=True, exist_ok=True)
    file_handler = RotatingFileHandler(
        log_dir / "juziscan.log",
        maxBytes=settings.log_max_bytes,
        backupCount=settings.log_backup_count,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)
    file_handler.addFilter(context_filter)

    payment_handler = RotatingFileHandler(
        log_dir / "payments.log",
        maxBytes=settings.log_max_bytes,
        backupCount=settings.log_backup_count,
        encoding="utf-8",
    )
    payment_handler.setFormatter(formatter)
    payment_handler.addFilter(context_filter)

    root_logger = logging.getLogger()
    root_logger.handlers.clear()
    root_logger.setLevel(level)
    root_logger.addHandler(console_handler)
    root_logger.addHandler(file_handler)

    payment_logger = logging.getLogger("payment.audit")
    payment_logger.handlers.clear()
    payment_logger.setLevel(level)
    payment_logger.addHandler(payment_handler)
    payment_logger.propagate = True
    logging.getLogger("uvicorn.access").setLevel(level)
