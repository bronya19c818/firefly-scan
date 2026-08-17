from __future__ import annotations

from decimal import Decimal
import logging
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from app.core.config import settings
from app.core.logging import configure_logging
from app.models.membership import Membership
from app.models.membership_plan import MembershipPlan
from app.models.order import Order
from app.services.payment_service import process_alipay_notify


class AlipayNotifyTests(unittest.TestCase):
    def setUp(self) -> None:
        self.payload = {
            "app_id": "app-123",
            "seller_id": "seller-456",
            "out_trade_no": "JZORDER001",
            "trade_no": "ALI001",
            "trade_status": "TRADE_SUCCESS",
            "total_amount": "19.00",
            "sign": "signed",
        }

    def _database(self, order: SimpleNamespace, plan: SimpleNamespace | None = None) -> MagicMock:
        db = MagicMock()
        order_query = MagicMock()
        order_query.filter.return_value.with_for_update.return_value.first.return_value = order
        plan_query = MagicMock()
        plan_query.filter.return_value.first.return_value = plan

        def query(model: type) -> MagicMock:
            if model is Order:
                return order_query
            if model is MembershipPlan:
                return plan_query
            raise AssertionError(f"Unexpected model query: {model}")

        db.query.side_effect = query
        db.order_query = order_query
        return db

    def _order(self, *, status: str = "pending", trade_no: str | None = None) -> SimpleNamespace:
        return SimpleNamespace(
            order_no="JZORDER001",
            user_id=7,
            plan_code="monthly",
            payment_channel="alipay",
            amount=Decimal("19.00"),
            status=status,
            paid_at=None,
            alipay_trade_no=trade_no,
        )

    @patch("app.services.payment_service.verify_notify_signature", return_value=True)
    def test_valid_notification_marks_paid_and_grants_once(self, _verify: MagicMock) -> None:
        order = self._order()
        db = self._database(order, SimpleNamespace(duration_days=30))

        with patch.object(settings, "alipay_app_id", "app-123"), patch.object(
            settings, "alipay_seller_id", "seller-456"
        ):
            result = process_alipay_notify(db, self.payload)

        self.assertTrue(result)
        self.assertEqual(order.status, "paid")
        self.assertEqual(order.alipay_trade_no, "ALI001")
        db.order_query.filter.return_value.with_for_update.assert_called_once_with()
        db.commit.assert_called_once_with()
        self.assertEqual(sum(isinstance(call.args[0], Membership) for call in db.add.call_args_list), 1)

    @patch("app.services.payment_service.verify_notify_signature", return_value=True)
    def test_duplicate_notification_does_not_grant_again(self, _verify: MagicMock) -> None:
        order = self._order(status="paid", trade_no="ALI001")
        db = self._database(order)

        with patch.object(settings, "alipay_app_id", "app-123"), patch.object(
            settings, "alipay_seller_id", "seller-456"
        ):
            result = process_alipay_notify(db, self.payload)

        self.assertTrue(result)
        db.commit.assert_called_once_with()
        self.assertFalse(any(isinstance(call.args[0], Membership) for call in db.add.call_args_list))

    @patch("app.services.payment_service.verify_notify_signature", return_value=True)
    def test_amount_mismatch_is_rejected(self, _verify: MagicMock) -> None:
        db = self._database(self._order())
        payload = {**self.payload, "total_amount": "0.01"}

        with patch.object(settings, "alipay_app_id", "app-123"), patch.object(
            settings, "alipay_seller_id", "seller-456"
        ):
            result = process_alipay_notify(db, payload)

        self.assertFalse(result)
        db.rollback.assert_called_once_with()
        db.commit.assert_not_called()

    @patch("app.services.payment_service.verify_notify_signature", return_value=True)
    def test_wrong_seller_is_rejected_before_database_access(self, _verify: MagicMock) -> None:
        db = self._database(self._order())
        payload = {**self.payload, "seller_id": "another-seller"}

        with patch.object(settings, "alipay_app_id", "app-123"), patch.object(
            settings, "alipay_seller_id", "seller-456"
        ):
            result = process_alipay_notify(db, payload)

        self.assertFalse(result)
        db.query.assert_not_called()

    @patch("app.services.payment_service.verify_notify_signature", return_value=True)
    def test_missing_plan_rolls_back_instead_of_granting_lifetime(self, _verify: MagicMock) -> None:
        db = self._database(self._order(), None)

        with patch.object(settings, "alipay_app_id", "app-123"), patch.object(
            settings, "alipay_seller_id", "seller-456"
        ):
            result = process_alipay_notify(db, self.payload)

        self.assertFalse(result)
        db.rollback.assert_called_once_with()
        db.commit.assert_not_called()


class PaymentAuditLoggingTests(unittest.TestCase):
    def test_payment_events_are_written_to_dedicated_log(self) -> None:
        with tempfile.TemporaryDirectory() as log_dir, patch.object(settings, "log_dir", log_dir):
            configure_logging()
            audit_logger = logging.getLogger("payment.audit")
            audit_logger.info("order_created order_no=TEST001")
            for handler in [*audit_logger.handlers, *logging.getLogger().handlers]:
                handler.flush()

            payment_log = Path(log_dir, "payments.log").read_text(encoding="utf-8")
            general_log = Path(log_dir, "juziscan.log").read_text(encoding="utf-8")

            self.assertIn("order_no=TEST001", payment_log)
            self.assertIn("order_no=TEST001", general_log)

            handlers = [*audit_logger.handlers, *logging.getLogger().handlers]
            audit_logger.handlers.clear()
            logging.getLogger().handlers.clear()
            for handler in handlers:
                handler.close()


if __name__ == "__main__":
    unittest.main()
