# Ducky 하드웨어

Raspberry Pi가 음성 대화와 서버 통신을 처리하고, Arduino Nano가 USB 시리얼로 물리 버튼 입력과 상태 LED를 연결합니다.

[제품 소개](../README.md) · [서버 기기 등록](../Ducky_was/README.md#라즈베리파이-기기-등록)

| 구성 | 담당 기능 | 가이드 |
| --- | --- | --- |
| Raspberry Pi | 마이크 녹음, STT/TTS, Spring 대화 요청, 상태 보고 | [Raspberry Voice Duck](raspberry_voice_duck/README.md) |
| Arduino Nano | 버튼 디바운스, 녹음 트리거 전달, 상태 LED 출력 | [Nano 버튼·LED](arduino/nano_button_led/README.md) |

웹에서 시작할 때는 등록된 Pi의 녹음 명령을 이용합니다. 물리 버튼으로 시작할 때는 Pi를 버튼 모드로 실행합니다.
