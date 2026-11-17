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

    def test_fetch_next_command_returns_claimed_command(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {
            "success": True,
            "data": {
                "available": True,
                "commandId": 7,
                "commandType": "START_RECORDING",
                "conversationId": 12,
            },
        }

        with patch.object(server_client.requests, "post", return_value=response) as post:
            command = server_client.fetch_next_command()

        self.assertEqual(
            {
                "available": True,
                "commandId": 7,
                "commandType": "START_RECORDING",
                "conversationId": 12,
            },
            command,
        )
        post.assert_called_once_with(
            "http://localhost:8080/api/iot/commands/next",
            json={"device_id": "raspberry-duck-001"},
            timeout=2.0,
        )

    def test_fetch_next_command_returns_none_when_empty(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {
            "success": True,
            "data": {
                "available": False,
                "commandId": None,
                "commandType": None,
                "conversationId": None,
            },
        }

        with patch.object(server_client.requests, "post", return_value=response):
            self.assertIsNone(server_client.fetch_next_command())

    def test_complete_command_posts_success_payload(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None

        with patch.object(server_client.requests, "post", return_value=response) as post:
            result = server_client.complete_command(7, True)

        self.assertTrue(result)
        post.assert_called_once_with(
            "http://localhost:8080/api/iot/commands/7/complete",
            json={
                "device_id": "raspberry-duck-001",
                "success": True,
            },
            timeout=2.0,
        )

    def test_fetch_active_conversation_id_returns_conversation_id(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {"conversationId": 12}

        with patch.object(server_client.requests, "get", return_value=response) as get:
            conversation_id = server_client.fetch_active_conversation_id()

        self.assertEqual(12, conversation_id)
        get.assert_called_once_with(
            "http://localhost:8080/api/duck/active-conversation",
            params={"deviceId": "raspberry-duck-001", "userId": "test-user"},
            timeout=2.0,
        )

    def test_fetch_active_conversation_id_returns_none_when_missing(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {"conversationId": None}

        with patch.object(server_client.requests, "get", return_value=response):
            self.assertIsNone(server_client.fetch_active_conversation_id())

    def test_send_message_to_server_includes_conversation_id(self) -> None:
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {
            "conversationId": 12,
            "message": "next question",
            "shouldSaveLog": True,
        }

        with patch.object(server_client.requests, "post", return_value=response) as post:
            data = server_client.send_message_to_server("hello", conversation_id=12)

        self.assertEqual("next question", data["message"])
        post.assert_called_once_with(
            "http://localhost:8080/api/duck/conversation",
            json={
                "deviceId": "raspberry-duck-001",
                "userId": "test-user",
                "inputType": "voice",
                "message": "hello",
                "learningType": {
                    "processing": "reflective",
                    "expression": "verbal",
                    "structure": "sequential",
                },
                "conversationId": 12,
            },
            timeout=30.0,
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
