from __future__ import annotations

import hashlib
import hmac
import json
import time
from datetime import UTC, datetime

import httpx
from fastapi import HTTPException, status

from app.core.config import settings

SERVICE = "sms"
HOST = "sms.tencentcloudapi.com"
ENDPOINT = f"https://{HOST}"
ALGORITHM = "TC3-HMAC-SHA256"
ACTION = "SendSms"
VERSION = "2021-01-11"


def send_login_code_sms(phone: str, code: str, expire_minutes: int) -> None:
    if settings.tencent_sms_debug_mode and not _is_sms_configured():
        print(f"[JuZiScan][SMS-DEBUG] phone={phone} code={code}")
        return

    if not _is_sms_configured():
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="腾讯云短信配置不完整，请检查服务端环境变量",
        )

    payload = {
        "PhoneNumberSet": [_format_e164_phone(phone)],
        "SmsSdkAppId": settings.tencent_sms_app_id,
        "SignName": settings.tencent_sms_sign_name,
        "TemplateId": settings.tencent_sms_template_id,
        "TemplateParamSet": _render_template_params(code, expire_minutes),
        "SessionContext": "juziscan-login",
    }
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    timestamp = int(time.time())
    authorization = _build_authorization(body, timestamp)
    headers = {
        "Authorization": authorization,
        "Content-Type": "application/json; charset=utf-8",
        "Host": HOST,
        "X-TC-Action": ACTION,
        "X-TC-Timestamp": str(timestamp),
        "X-TC-Version": VERSION,
        "X-TC-Region": settings.tencent_sms_region,
    }

    with httpx.Client(timeout=10.0) as client:
        response = client.post(ENDPOINT, content=body.encode("utf-8"), headers=headers)
    response.raise_for_status()
    data = response.json().get("Response", {})
    error = data.get("Error")
    if error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"腾讯云短信发送失败：{error.get('Code')} {error.get('Message')}",
        )
    statuses = data.get("SendStatusSet") or []
    if not statuses or statuses[0].get("Code") != "Ok":
        first = statuses[0] if statuses else {}
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"短信发送失败：{first.get('Code', 'Unknown')} {first.get('Message', 'Unknown error')}",
        )


def _is_sms_configured() -> bool:
    return all(
        [
            settings.tencent_sms_app_id,
            settings.tencent_sms_secret_id,
            settings.tencent_sms_secret_key,
            settings.tencent_sms_sign_name,
            settings.tencent_sms_template_id,
        ]
    )


def _render_template_params(code: str, expire_minutes: int) -> list[str]:
    template = settings.tencent_sms_template_params or "{code}"
    parts = [part.strip() for part in template.split(",") if part.strip()]
    return [part.format(code=code, minutes=expire_minutes) for part in parts] or [code]


def _format_e164_phone(phone: str) -> str:
    return f"+86{phone}"


def _build_authorization(body: str, timestamp: int) -> str:
    date = datetime.fromtimestamp(timestamp, UTC).strftime("%Y-%m-%d")
    canonical_headers = (
        "content-type:application/json; charset=utf-8\n"
        f"host:{HOST}\n"
        f"x-tc-action:{ACTION.lower()}\n"
    )
    signed_headers = "content-type;host;x-tc-action"
    hashed_payload = hashlib.sha256(body.encode("utf-8")).hexdigest()
    canonical_request = "\n".join(
        ["POST", "/", "", canonical_headers, signed_headers, hashed_payload]
    )
    credential_scope = f"{date}/{SERVICE}/tc3_request"
    string_to_sign = "\n".join(
        [
            ALGORITHM,
            str(timestamp),
            credential_scope,
            hashlib.sha256(canonical_request.encode("utf-8")).hexdigest(),
        ]
    )
    secret_date = _sign(("TC3" + settings.tencent_sms_secret_key).encode("utf-8"), date)
    secret_service = _sign(secret_date, SERVICE)
    secret_signing = _sign(secret_service, "tc3_request")
    signature = hmac.new(secret_signing, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
    return (
        f"{ALGORITHM} Credential={settings.tencent_sms_secret_id}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )


def _sign(key: bytes, message: str) -> bytes:
    return hmac.new(key, message.encode("utf-8"), hashlib.sha256).digest()
