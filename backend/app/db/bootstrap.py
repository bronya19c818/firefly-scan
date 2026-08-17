from __future__ import annotations

from sqlalchemy import inspect, text

from app.db.session import engine


def ensure_database_ready() -> None:
    _ensure_users_is_admin_column()
    _ensure_users_phone_column()
    _ensure_users_username_column()
    _ensure_users_avatar_url_column()
    _ensure_users_username_unique_index()
    _ensure_users_phone_unique_index()
    _ensure_users_email_nullable()
    _remove_synthetic_user_emails()
    _ensure_sms_failed_attempts_column()
    _ensure_orders_integrity()
    _rename_daily_membership_plan_code()
    _remove_legacy_backend_admin_users_once()
    _ensure_feedbacks_user_id_column()
    _ensure_default_app_version()
    _ensure_default_membership_plans()


def _rename_daily_membership_plan_code() -> None:
    """Persist the public daily plan code in all existing payment records."""
    inspector = inspect(engine)
    table_names = set(inspector.get_table_names())
    with engine.begin() as connection:
        if "orders" in table_names:
            connection.execute(
                text("UPDATE orders SET plan_code = 'daily' WHERE plan_code = 'test_001'")
            )
        if "memberships" in table_names:
            connection.execute(
                text("UPDATE memberships SET plan_code = 'daily' WHERE plan_code = 'test_001'")
            )


def _ensure_orders_integrity() -> None:
    inspector = inspect(engine)
    if "orders" not in inspector.get_table_names():
        return

    indexes = inspector.get_indexes("orders")
    constraints = inspector.get_unique_constraints("orders")
    has_unique_trade_no = any(
        item.get("column_names") == ["alipay_trade_no"]
        for item in [*indexes, *constraints]
        if item.get("unique", True)
    )

    with engine.begin() as connection:
        invalid_status = connection.execute(
            text(
                "SELECT status FROM orders "
                "WHERE status NOT IN ('pending', 'paid', 'closed', 'refunded') LIMIT 1"
            )
        ).scalar_one_or_none()
        if invalid_status is not None:
            raise RuntimeError(f"orders 表存在不支持的状态: {invalid_status}")

        duplicate_trade_no = connection.execute(
            text(
                "SELECT alipay_trade_no FROM orders WHERE alipay_trade_no IS NOT NULL "
                "GROUP BY alipay_trade_no HAVING COUNT(*) > 1 LIMIT 1"
            )
        ).scalar_one_or_none()
        if duplicate_trade_no is not None:
            raise RuntimeError("orders 表存在重复支付宝交易号，请清理后再创建唯一索引")

        if not has_unique_trade_no:
            connection.execute(
                text(
                    "CREATE UNIQUE INDEX ix_orders_alipay_trade_no_unique "
                    "ON orders (alipay_trade_no) WHERE alipay_trade_no IS NOT NULL"
                )
            )

        status_constraint_exists = connection.execute(
            text(
                "SELECT 1 FROM pg_constraint "
                "WHERE conname = 'ck_orders_status' AND conrelid = 'orders'::regclass"
            )
        ).scalar_one_or_none()
        if status_constraint_exists is None:
            connection.execute(
                text(
                    "ALTER TABLE orders ADD CONSTRAINT ck_orders_status "
                    "CHECK (status IN ('pending', 'paid', 'closed', 'refunded'))"
                )
            )


def _ensure_users_is_admin_column() -> None:
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return

    columns = {column["name"] for column in inspector.get_columns("users")}
    if "is_admin" in columns:
        return

    statement = text(
        "ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE"
    )
    with engine.begin() as connection:
        connection.execute(statement)


def _ensure_users_phone_column() -> None:
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return

    columns = {column["name"] for column in inspector.get_columns("users")}
    if "phone" in columns:
        return

    statement = text("ALTER TABLE users ADD COLUMN phone VARCHAR(32)")
    with engine.begin() as connection:
        connection.execute(statement)


def _ensure_users_phone_unique_index() -> None:
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return

    indexes = inspector.get_indexes("users")
    constraints = inspector.get_unique_constraints("users")
    has_unique_phone = any(
        item.get("column_names") == ["phone"]
        for item in [*indexes, *constraints]
        if item.get("unique", True)
    )
    if has_unique_phone:
        return

    with engine.begin() as connection:
        duplicate = connection.execute(
            text(
                "SELECT phone FROM users WHERE phone IS NOT NULL "
                "GROUP BY phone HAVING COUNT(*) > 1 LIMIT 1"
            )
        ).scalar_one_or_none()
        if duplicate is not None:
            raise RuntimeError("users 表存在重复手机号，请清理后再创建唯一索引")
        connection.execute(text("CREATE UNIQUE INDEX ix_users_phone_unique ON users (phone)"))


def _ensure_users_username_column() -> None:
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return
    columns = {column["name"] for column in inspector.get_columns("users")}
    if "username" not in columns:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE users ADD COLUMN username VARCHAR(32)"))


def _ensure_users_avatar_url_column() -> None:
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return
    columns = {column["name"] for column in inspector.get_columns("users")}
    if "avatar_url" not in columns:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255)"))


def _ensure_users_username_unique_index() -> None:
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return
    indexes = inspector.get_indexes("users")
    constraints = inspector.get_unique_constraints("users")
    if any(
        item.get("column_names") == ["username"]
        for item in [*indexes, *constraints]
        if item.get("unique", True)
    ):
        return
    with engine.begin() as connection:
        connection.execute(text("CREATE UNIQUE INDEX ix_users_username_unique ON users (username)"))


def _ensure_users_email_nullable() -> None:
    """Email is an optional contact method, not a storage slot for login identifiers."""
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return
    email_column = next(
        (column for column in inspector.get_columns("users") if column["name"] == "email"),
        None,
    )
    if email_column is None or email_column.get("nullable", True):
        return
    with engine.begin() as connection:
        connection.execute(text("ALTER TABLE users ALTER COLUMN email DROP NOT NULL"))


def _remove_synthetic_user_emails() -> None:
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return
    with engine.begin() as connection:
        connection.execute(
            text(
                "UPDATE users SET email = NULL WHERE is_admin = FALSE AND ("
                "email LIKE 'user-%@account.juziscan.cn' OR "
                "email LIKE 'phone-%@mobile.juziscan.cn')"
            )
        )


def _ensure_sms_failed_attempts_column() -> None:
    inspector = inspect(engine)
    if "sms_verification_codes" not in inspector.get_table_names():
        return

    columns = {column["name"] for column in inspector.get_columns("sms_verification_codes")}
    if "failed_attempts" in columns:
        return

    statement = text(
        "ALTER TABLE sms_verification_codes "
        "ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0"
    )
    with engine.begin() as connection:
        connection.execute(statement)


def _remove_legacy_backend_admin_users_once() -> None:
    """Remove user rows created by the old, mixed backend-admin design once.

    The migration marker is important: ``users.is_admin`` is retained for a
    future App-side role and those future users must not be deleted on restart.
    """
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return

    migration = "20260712_separate_backend_admin_from_users"
    with engine.begin() as connection:
        connection.execute(
            text(
                "CREATE TABLE IF NOT EXISTS app_migrations ("
                "name VARCHAR(255) PRIMARY KEY)"
            )
        )
        already_applied = connection.execute(
            text("SELECT name FROM app_migrations WHERE name = :name"),
            {"name": migration},
        ).scalar_one_or_none()
        if already_applied is not None:
            return

        # These relations cascade at ORM level only, so remove any legacy
        # dependent rows explicitly before deleting the old admin users.
        connection.execute(
            text("DELETE FROM memberships WHERE user_id IN (SELECT id FROM users WHERE is_admin = TRUE)")
        )
        connection.execute(
            text("DELETE FROM orders WHERE user_id IN (SELECT id FROM users WHERE is_admin = TRUE)")
        )
        connection.execute(text("DELETE FROM users WHERE is_admin = TRUE"))
        connection.execute(
            text("INSERT INTO app_migrations (name) VALUES (:name)"),
            {"name": migration},
        )


def _ensure_feedbacks_user_id_column() -> None:
    inspector = inspect(engine)
    if "feedbacks" not in inspector.get_table_names():
        return

    columns = {column["name"] for column in inspector.get_columns("feedbacks")}
    if "user_id" in columns:
        return

    statement = text(
        "ALTER TABLE feedbacks ADD COLUMN user_id INTEGER REFERENCES users(id) ON DELETE SET NULL"
    )
    with engine.begin() as connection:
        connection.execute(statement)


def _ensure_default_app_version() -> None:
    inspector = inspect(engine)
    if "app_versions" not in inspector.get_table_names():
        return
    with engine.begin() as connection:
        count = connection.execute(text("SELECT COUNT(*) FROM app_versions")).scalar()
        if count == 0:
            connection.execute(
                text(
                    "INSERT INTO app_versions (version_code, version_name, download_url, update_log, is_force_update, created_at) "
                    "VALUES (:version_code, :version_name, :download_url, :update_log, :is_force_update, CURRENT_TIMESTAMP)"
                ),
                {
                    "version_code": 100,
                    "version_name": "1.0.0",
                    "download_url": "https://example.com/app-release.apk",
                    "update_log": "初始版本发布",
                    "is_force_update": False,
                }
            )


def _ensure_default_membership_plans() -> None:
    inspector = inspect(engine)
    if "membership_plans" not in inspector.get_table_names():
        return
    with engine.begin() as connection:
        count = connection.execute(text("SELECT COUNT(*) FROM membership_plans")).scalar()
        if count == 0:
            connection.execute(
                text(
                    "INSERT INTO membership_plans (plan_code, title, price, original_price, duration_days, bottom_label, tag_text, is_default, is_active, sort_order, created_at, updated_at) "
                    "VALUES "
                    "('daily', '日会员', 0.01, 2.00, 1, '会员有效期1天', NULL, FALSE, TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    "('lifetime', '永久会员', 199.00, 599.00, NULL, '一次付费 终身使用', '老用户福利', FALSE, TRUE, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    "('annual', '年度会员', 99.00, 288.00, 365, '0.27元/天', '超值推荐', TRUE, TRUE, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    "('quarterly', '季度会员', 39.00, 78.00, 90, '0.43元/天', NULL, FALSE, TRUE, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    "('monthly', '月度会员', 19.00, 30.00, 30, '0.63元/天', NULL, FALSE, TRUE, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    "('trial_5d', '体验会员(5天)', 0.00, NULL, 5, '体验会员', NULL, FALSE, FALSE, 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
                )
            )
