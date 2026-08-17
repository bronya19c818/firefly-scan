from __future__ import annotations

import base64
from collections import OrderedDict
from datetime import datetime
from urllib.parse import quote_plus

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

from app.core.config import settings


def build_app_order_string(order_no: str, amount: str, subject: str, body: str) -> str:
    _ensure_alipay_order_configured()
    params = OrderedDict(
        [
            ("app_id", settings.alipay_app_id),
            ("biz_content", _compact_json(order_no=order_no, amount=amount, subject=subject, body=body)),
            ("charset", "utf-8"),
            ("method", "alipay.trade.app.pay"),
            ("notify_url", settings.alipay_notify_url),
            ("sign_type", "RSA2"),
            ("timestamp", datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
            ("version", "1.0"),
        ]
    )
    unsigned_string = "&".join(f"{key}={value}" for key, value in params.items())
    sign = _sign(unsigned_string)
    encoded = [f"{key}={quote_plus(value)}" for key, value in params.items()]
    encoded.append(f"sign={quote_plus(sign)}")
    return "&".join(encoded)


def verify_notify_signature(data: dict[str, str]) -> bool:
    _ensure_alipay_notify_configured()
    sign = data.get("sign")
    if not sign:
        return False

    unsigned_items = sorted(
        (key, value)
        for key, value in data.items()
        if key not in {"sign", "sign_type"} and value is not None and value != ""
    )
    unsigned_string = "&".join(f"{key}={value}" for key, value in unsigned_items)
    public_key = serialization.load_pem_public_key(
        _normalize_pem(settings.alipay_alipay_public_key, "PUBLIC KEY").encode("utf-8")
    )
    try:
        public_key.verify(
            base64.b64decode(sign),
            unsigned_string.encode("utf-8"),
            padding.PKCS1v15(),
            hashes.SHA256(),
        )
        return True
    except (InvalidSignature, ValueError):
        return False


def _sign(content: str) -> str:
    private_key = serialization.load_pem_private_key(
        _normalize_pem(settings.alipay_app_private_key, "PRIVATE KEY").encode("utf-8"),
        password=None,
    )
    signature = private_key.sign(
        content.encode("utf-8"),
        padding.PKCS1v15(),
        hashes.SHA256(),
    )
    return base64.b64encode(signature).decode("utf-8")


def _compact_json(order_no: str, amount: str, subject: str, body: str) -> str:
    return (
        "{"
        f"\"body\":\"{body}\","
        f"\"subject\":\"{subject}\","
        f"\"out_trade_no\":\"{order_no}\","
        f"\"timeout_express\":\"30m\","
        f"\"total_amount\":\"{amount}\","
        "\"product_code\":\"QUICK_MSECURITY_PAY\""
        "}"
    )


def _ensure_alipay_order_configured() -> None:
    if not settings.alipay_app_id or not settings.alipay_notify_url:
        raise ValueError("Alipay configuration is incomplete")
    if not settings.alipay_app_private_key:
        raise ValueError("Alipay private key configuration is incomplete")
    if not settings.alipay_alipay_public_key or not settings.alipay_seller_id:
        raise ValueError("Alipay notification configuration is incomplete")


def _ensure_alipay_notify_configured() -> None:
    if not settings.alipay_app_id or not settings.alipay_seller_id:
        raise ValueError("Alipay notification identity configuration is incomplete")
    if not settings.alipay_alipay_public_key:
        raise ValueError("Alipay public key configuration is incomplete")


def _normalize_pem(value: str, key_type: str) -> str:
    normalized = value.replace("\\n", "\n").strip()
    if "-----BEGIN " in normalized:
        return normalized
    compact = "".join(normalized.split())
    lines = [compact[index:index + 64] for index in range(0, len(compact), 64)]
    return "\n".join(
        [f"-----BEGIN {key_type}-----", *lines, f"-----END {key_type}-----"]
    )
