# Raspberry Voice Duck

Raspberry Pi에서 동작하는 음성 기반 러버덕 AI MVP입니다. 사용자가 문제를 말하면 Pi가 녹음하고, OpenAI STT로 텍스트를 만들고, Spring Boot 서버에 대화 요청을 보낸 뒤 응답을 OpenAI TTS로 변환해 스피커로 출력합니다. 서버가 꺼져 있거나 응답 형식이 맞지 않으면 로컬 mock 러버덕 응답으로 계속 진행합니다.

## MVP 흐름

```txt
마이크 녹음
  -> OpenAI STT
  -> Spring Boot /api/duck/conversation
  -> 서버 응답 또는 로컬 fallback
  -> OpenAI TTS
  -> 스피커 출력
  -> 서버 로그 저장
```

## 파일 구조

```txt
raspberry_voice_duck/
├── main.py
├── audio_io.py
├── stt.py
├── tts.py
├── server_client.py
├── duck_prompt.py
├── config.py
├── .env.example
├── requirements.txt
├── README.md
└── audio/
```

## Raspberry Pi 준비

Raspberry Pi OS 64-bit, Python 3.10 이상을 기준으로 합니다.

```bash
sudo apt update
sudo apt install -y ffmpeg mpg123 alsa-utils
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## 환경변수 설정

```bash
cp .env.example .env
nano .env
```

필수 값:

```env
OPENAI_API_KEY=sk-your-openai-api-key
SERVER_BASE_URL=http://서버주소:8080
```

기본 OpenAI 모델:

```env
OPENAI_STT_MODEL=gpt-4o-transcribe
OPENAI_TTS_MODEL=gpt-4o-mini-tts
OPENAI_TTS_VOICE=marin
```

마이크나 스피커 장치를 직접 지정해야 하면 `arecord -l`, `aplay -l` 결과를 보고 `MIC_DEVICE`, `SPEAKER_DEVICE`에 `plughw:1,0` 같은 값을 넣습니다.

## 실행

```bash
cd raspberry_voice_duck
source venv/bin/activate
python main.py
```

프로그램은 반복 루프로 동작합니다. 종료하려면 `Ctrl+C`를 누릅니다.

## 장치 테스트

마이크 확인:

```bash
arecord -l
arecord -D plughw:1,0 -f cd -t wav -d 5 test.wav
aplay test.wav
```

스피커 확인:

```bash
aplay -l
speaker-test -t wav -c 2
mpg123 audio/response.mp3
```

장치 번호가 다르면 `plughw:1,0`을 실제 값으로 바꿉니다.

## 서버 API 계약

### Health

```http
GET /api/health
```

정상 응답:

```json
{
  "status": "ok"
}
```

### Conversation

```http
POST /api/duck/conversation
Content-Type: application/json
```

요청:

```json
{
  "deviceId": "raspberry-duck-001",
  "userId": "test-user",
  "inputType": "voice",
  "message": "파이썬 반복문에서 인덱스 오류가 나는데 왜 그런지 모르겠어",
  "learningType": {
    "processing": "reflective",
    "expression": "verbal",
    "structure": "sequential"
  }
}
```

응답:

```json
{
  "conversationId": 15,
  "responseType": "question",
  "message": "좋아요. 먼저 인덱스 오류가 발생한 반복문의 범위를 확인해볼까요?",
  "hintLevel": 1,
  "shouldSaveLog": true
}
```

클라이언트는 `message`가 비어 있거나 JSON이 아니면 서버 응답을 실패로 보고 로컬 fallback을 사용합니다.

### Conversation logs

```http
POST /api/duck/conversation/logs
Content-Type: application/json
```

요청:

```json
{
  "conversationId": 15,
  "deviceId": "raspberry-duck-001",
  "userId": "test-user",
  "userMessage": "파이썬 반복문에서 인덱스 오류가 나는데 왜 그런지 모르겠어",
  "assistantMessage": "리스트의 길이와 접근하려는 인덱스가 각각 얼마인지 말해볼래요?",
  "sttSuccess": true,
  "ttsSuccess": true
}
```

`conversationId`는 서버 응답에 없으면 생략됩니다.

## Fallback 동작

다음 상황에서는 프로그램을 종료하지 않고 로컬 러버덕 응답으로 대체합니다.

- `/api/health` 실패
- `/api/duck/conversation` timeout 또는 HTTP 오류
- 서버 응답이 JSON이 아님
- 서버 응답에 `message`가 없거나 비어 있음

fallback 응답은 정답을 바로 주지 않고, 사용자의 발화를 짧게 요약한 뒤 다음 사고 단계를 묻습니다.

## 모듈별 단독 확인

Python 인터프리터에서 간단히 확인할 수 있습니다.

```python
from duck_prompt import generate_local_duck_response
print(generate_local_duck_response("파이썬 반복문에서 인덱스 오류가 나"))
```

```python
from stt import transcribe_audio
print(transcribe_audio("audio/input.wav"))
```

```python
from tts import synthesize_speech
synthesize_speech("좋아요. 문제를 한 단계씩 설명해볼까요?", "audio/response.mp3")
```

## 후속 확장

- Spring Boot 서버에서 LLM 기반 러버덕 질문 생성 구현
- MySQL 대화 로그 저장과 조회 API 구현
- 웹/PWA에서 기기 등록, 학습 기록, 학습 유형 설정 구현
- 오프라인 모드가 필요하면 STT는 faster-whisper, TTS는 Piper로 교체
