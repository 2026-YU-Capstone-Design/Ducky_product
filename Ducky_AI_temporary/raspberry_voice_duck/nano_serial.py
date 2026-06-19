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
                dsrdtr=False,
                rtscts=False,
            )
            # DTR 토글로 Nano가 리셋되는 것을 줄인다.
            self._serial.dtr = False
            self._serial.reset_input_buffer()
            time.sleep(0.3)
            logger.info("Connected to Arduino Nano on %s", self._port)
            return True
        except SerialException:
            logger.warning("Failed to connect to Nano serial on %s", self._port)
            self._serial = None
            return False

    def _handle_serial_error(self, action: str) -> None:
        logger.info("Nano serial %s failed; will reconnect", action)
        self.close()

    def send_led_state(self, state_name: str) -> bool:
        if not self._connect_if_needed():
            return False

        assert self._serial is not None
        try:
            self._serial.write(f"LED:{state_name}\n".encode("utf-8"))
            self._serial.flush()
            return True
        except SerialException:
            self._handle_serial_error("LED write")
            return False

    def read_button_event(self) -> bool:
        if not self._connect_if_needed():
            return False

        assert self._serial is not None
        try:
            if self._serial.in_waiting <= 0:
                return False

            raw_line = self._serial.readline().decode("utf-8", errors="ignore").strip()
        except SerialException:
            self._handle_serial_error("read")
            return False

        if raw_line and raw_line != "BTN:PRESS":
            logger.debug("Ignored Nano serial line: %r", raw_line)

        return raw_line == "BTN:PRESS"

    def close(self) -> None:
        if not self._serial:
            return
        try:
            self._serial.close()
        except SerialException:
            logger.warning("Failed to close Nano serial cleanly")
        finally:
            self._serial = None
