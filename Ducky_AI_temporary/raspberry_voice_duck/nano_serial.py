import logging
import time

import serial
from serial import SerialException


logger = logging.getLogger(__name__)


class NanoSerialAdapter:
    def __init__(
        self,
        port: str,
        baud_rate: int = 115200,
        timeout_seconds: float = 0.1,
        reconnect_seconds: float = 2.0,
    ) -> None:
        self._port = port
        self._baud_rate = baud_rate
        self._timeout_seconds = timeout_seconds
        self._reconnect_seconds = reconnect_seconds
        self._serial: serial.Serial | None = None
        self._last_connect_attempt = 0.0

    def _connect_if_needed(self) -> bool:
        if self._serial and self._serial.is_open:
            return True

        now = time.monotonic()
        if now - self._last_connect_attempt < self._reconnect_seconds:
            return False

        self._last_connect_attempt = now
        try:
            self._serial = serial.Serial(
                self._port,
                baudrate=self._baud_rate,
                timeout=self._timeout_seconds,
            )
            logger.info("Connected to Arduino Nano on %s", self._port)
            return True
        except SerialException:
            logger.warning("Failed to connect to Nano serial on %s", self._port, exc_info=True)
            self._serial = None
            return False

    def send_led_state(self, state_name: str) -> bool:
        if not self._connect_if_needed():
            return False

        assert self._serial is not None
        try:
            self._serial.write(f"LED:{state_name}\n".encode("utf-8"))
            self._serial.flush()
            return True
        except SerialException:
            logger.warning("Failed to send LED state to Nano", exc_info=True)
            self.close()
            return False

    def read_button_event(self) -> bool:
        if not self._connect_if_needed():
            return False

        assert self._serial is not None
        try:
            raw_line = self._serial.readline().decode("utf-8", errors="ignore").strip()
        except SerialException:
            logger.warning("Failed while reading Nano serial", exc_info=True)
            self.close()
            return False

        return raw_line == "BTN:PRESS"

    def close(self) -> None:
        if not self._serial:
            return
        try:
            self._serial.close()
        except SerialException:
            logger.warning("Failed to close Nano serial cleanly", exc_info=True)
        finally:
            self._serial = None
