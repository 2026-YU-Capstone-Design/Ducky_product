import sys
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakeRequestException(Exception):
    pass


fake_requests = types.SimpleNamespace(
    RequestException=FakeRequestException,
    get=Mock(),
    post=Mock(),
)
sys.modules.setdefault("requests", fake_requests)
sys.modules.setdefault("dotenv", types.SimpleNamespace(load_dotenv=lambda *_args, **_kwargs: None))

import server_client  # noqa: E402
from led_state import LedState  # noqa: E402


class ServerClientTelemetryTest(unittest.TestCase):
    def setUp(self) -> None:
        server_client._iot_backoff_until = 0.0

    def test_report_iot_state_posts_state_payload(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None

        with patch.object(server_client.requests, "post", return_value=response) as post:
            result = server_client.report_iot_state(LedState.RECORDING)

        self.assertTrue(result)
        post.assert_called_once_with(
            "http://localhost:8080/api/iot/state",
            json={
                "device_id": "raspberry-duck-001",
                "current_state": "RECORDING",
            },
            timeout=2.0,
        )

    def test_report_iot_error_posts_error_payload(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None

        with patch.object(server_client.requests, "post", return_value=response) as post:
            result = server_client.report_iot_error("STT_FAILED", "failed")

        self.assertTrue(result)
        post.assert_called_once_with(
            "http://localhost:8080/api/iot/error",
            json={
                "device_id": "raspberry-duck-001",
                "errorCode": "STT_FAILED",
                "errorMessage": "failed",
            },
            timeout=2.0,
        )

    def test_telemetry_failure_uses_backoff(self) -> None:
        with patch.object(
            server_client.requests,
            "post",
            side_effect=server_client.requests.RequestException("server down"),
        ) as post:
            first_result = server_client.report_iot_state(LedState.RECORDING)
            second_result = server_client.report_iot_state(LedState.SPEAKING)

        self.assertFalse(first_result)
        self.assertFalse(second_result)
        self.assertEqual(1, post.call_count)


if __name__ == "__main__":
    unittest.main()
