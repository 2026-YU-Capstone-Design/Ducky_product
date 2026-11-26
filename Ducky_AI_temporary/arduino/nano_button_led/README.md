# Arduino Nano 버튼·LED

[Nano 펌웨어](nano_button_led.ino)는 버튼 입력을 Raspberry Pi에 전달하고, Pi가 보내는 상태에 따라 LED를 켭니다.

[하드웨어 구성](../../README.md) · [Raspberry Pi 클라이언트](../../raspberry_voice_duck/README.md)

## 핀과 시리얼 메시지

| 핀 | 역할 | 신호 |
| --- | --- | --- |
| D8 | 버튼 입력 | `INPUT_PULLUP`에서 LOW를 누름으로 인식, 50 ms 디바운스 |
| D1 | 대기 LED | `LED:IDLE` |
| D2 | 녹음 LED | `LED:LISTENING` |
| D3 | 응답 LED | `LED:RESPONDING` |
| D4 | 오프라인 LED | `LED:OFFLINE` |

1. Arduino IDE에서 [스케치](nano_button_led.ino)를 열고 연결된 Nano 보드와 포트를 선택해 업로드합니다.
2. Nano를 Pi에 USB로 연결하고 Pi 설정에서 `TRIGGER_MODE=button`과 실제 `NANO_SERIAL_PORT`를 지정합니다.
3. Pi 클라이언트를 실행하고 버튼 입력과 LED 전환을 확인합니다. 시리얼 속도는 **115200 baud**입니다.

Nano는 버튼이 눌릴 때 줄 단위로 `BTN:PRESS`를 보냅니다. Pi는 줄 단위의 `LED:<상태>` 메시지로 LED를 제어합니다.
