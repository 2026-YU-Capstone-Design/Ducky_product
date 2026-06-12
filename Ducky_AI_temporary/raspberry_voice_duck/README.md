# Raspberry Voice Duck

Raspberry Pi에서 동작하는 음성 기반 러버덕 AI 클라이언트입니다. 사용자가 문제를 말하면 Pi가 녹음하고, OpenAI STT로 텍스트를 만든 뒤 Spring Boot 서버의 `/api/duck/conversation`으로 전달합니다. 서버 응답은 OpenAI TTS로 변환해 스피커로 출력합니다.

서버 연결 실패나 응답 형식 오류가 발생하면 로컬 답변을 만들지 않습니다. 오류 상태를 보고하고 서버 상태 확인 메시지만 출력합니다.

## Flow

```txt
마이크 녹음
  -> OpenAI STT
  -> Spring Boot /api/duck/conversation
  -> OpenAI TTS
  -> 스피커 출력
  -> 서버 로그 저장
```

## Files

```txt
raspberry_voice_duck/
├── main.py
├── audio_io.py
├── stt.py
├── tts.py
├── server_client.py
├── config.py
├── .env.example
├── requirements.txt
├── README.md
└── audio/
```

## Setup

```bash
sudo apt update
sudo apt install -y ffmpeg mpg123 alsa-utils
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Create `.env`:

```bash
cp .env.example .env
nano .env
```

Required values:

```env
OPENAI_API_KEY=your-openai-api-key
SERVER_BASE_URL=http://server-address:8080
```

Optional OpenAI defaults:

```env
OPENAI_STT_MODEL=gpt-4o-transcribe
OPENAI_TTS_MODEL=gpt-4o-mini-tts
OPENAI_TTS_VOICE=marin
```

## Run

```bash
python main.py
```

Set microphone or speaker devices with `MIC_DEVICE` and `SPEAKER_DEVICE` when needed.

## Server API

### Health

```http
GET /api/health
```

### Conversation

```http
POST /api/duck/conversation
Content-Type: application/json
```

Request:

```json
{
  "deviceId": "raspberry-duck-001",
  "userId": "user-id",
  "inputType": "voice",
  "message": "파이썬 반복문에서 인덱스 오류가 나는데 왜 그런지 모르겠어",
  "learningType": {
    "processing": "reflective",
    "expression": "verbal",
    "structure": "sequential"
  }
}
```

Response:

```json
{
  "conversationId": 15,
  "responseType": "question",
  "message": "좋아요. 먼저 인덱스 오류가 발생한 반복문의 범위를 확인해볼까요?",
  "hintLevel": 1,
  "shouldSaveLog": true
}
```

The `message` field must be a non-empty string. Invalid server responses are treated as errors.

### Conversation Logs

```http
POST /api/duck/conversation/logs
Content-Type: application/json
```

Request:

```json
{
  "conversationId": 15,
  "deviceId": "raspberry-duck-001",
  "userId": "user-id",
  "userMessage": "파이썬 반복문에서 인덱스 오류가 나는데 왜 그런지 모르겠어",
  "assistantMessage": "좋아요. 먼저 인덱스 오류가 발생한 반복문의 범위를 확인해볼까요?",
  "sttSuccess": true,
  "ttsSuccess": true
}
```

If the server does not return a usable conversation response, the client does not save a conversation log for that turn.
