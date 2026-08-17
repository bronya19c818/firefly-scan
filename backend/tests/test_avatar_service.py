from __future__ import annotations

import base64
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from fastapi import HTTPException

from app.core.config import settings
from app.services.avatar_service import save_avatar


class AvatarServiceTests(unittest.TestCase):
    def test_saves_avatar_and_replaces_previous_local_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory, patch.object(
            settings, "avatar_storage_dir", directory
        ):
            previous = Path(directory, "previous.jpg")
            previous.write_bytes(b"old")
            user = SimpleNamespace(
                id=7,
                avatar_url="/uploads/avatars/previous.jpg",
            )
            db = MagicMock()
            payload = base64.b64encode(b"\xff\xd8\xffavatar").decode("ascii")

            result = save_avatar(db, user, payload, "image/jpeg")

            self.assertIs(result, user)
            self.assertTrue(user.avatar_url.startswith("/uploads/avatars/user-7-"))
            self.assertTrue(Path(directory, user.avatar_url.rsplit("/", 1)[-1]).is_file())
            self.assertFalse(previous.exists())
            db.commit.assert_called_once_with()

    def test_rejects_content_that_does_not_match_image_type(self) -> None:
        user = SimpleNamespace(id=7, avatar_url=None)
        payload = base64.b64encode(b"not-an-image").decode("ascii")

        with self.assertRaises(HTTPException) as error:
            save_avatar(MagicMock(), user, payload, "image/png")

        self.assertEqual(error.exception.status_code, 422)


if __name__ == "__main__":
    unittest.main()
