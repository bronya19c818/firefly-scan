from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "JuZiScan API"
    app_env: str = "development"
    app_host: str = "0.0.0.0"
    app_port: int = 18001
    log_level: str = "INFO"
    log_dir: str = "logs"
    log_max_bytes: int = 10 * 1024 * 1024
    log_backup_count: int = 10
    avatar_storage_dir: str = "storage/avatars"
    avatar_max_bytes: int = 5 * 1024 * 1024
    secret_key: str = "change-me-in-production"
    access_token_expire_minutes: int = 60 * 24 * 7
    database_url: str = "sqlite:///./juziscan.db"
    bootstrap_admin_email: str = ""
    bootstrap_admin_password: str = ""
    bootstrap_admin_nickname: str = "Admin"
    alipay_app_id: str = ""
    alipay_gateway: str = "https://openapi.alipay.com/gateway.do"
    alipay_notify_url: str = ""
    alipay_app_private_key: str = ""
    alipay_seller_id: str = ""
    sms_code_expire_minutes: int = 5
    sms_code_resend_interval_seconds: int = 60
    sms_code_max_attempts: int = 5
    tencent_sms_app_id: str = ""
    tencent_sms_secret_id: str = ""
    tencent_sms_secret_key: str = ""
    tencent_sms_sign_name: str = ""
    tencent_sms_template_id: str = ""
    tencent_sms_region: str = "ap-guangzhou"
    tencent_sms_template_params: str = "{code}"
    tencent_sms_debug_mode: bool = True
    alipay_alipay_public_key: str = ""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", case_sensitive=False)


settings = Settings()
