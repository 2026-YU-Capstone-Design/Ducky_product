# Ducky WAS 🦆

> 음성인식 기반 러버덕 AI 학습 시스템 백엔드

## 기술 스택
- Java 17
- Spring Boot 4.0.6
- PostgreSQL 17
- Redis

## 로컬 실행 방법

### 1. PostgreSQL 설치 및 설정
```sql
CREATE USER rubberduck WITH PASSWORD 'your-local-db-password';
CREATE DATABASE rubberduck OWNER rubberduck;
GRANT ALL PRIVILEGES ON DATABASE rubberduck TO rubberduck;
```

### 2. pgvector 설치
```bash
brew install pgvector  # Mac
```
```sql
-- postgres 슈퍼유저로 접속 후
\c rubberduck
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. .env 파일 생성
프로젝트 루트에 `.env` 파일 직접 생성:

## 라즈베리파이 기기 등록

로그인한 계정의 숫자 ID(`GET /api/users/me`의 `data.id`)와 기기 시리얼을 확인한다. 웹 로그인은 기기 없이도 동작한다. 기존 자동 연결 기록에는 기기 키가 없으므로 운영자가 확인한 소유자를 한 번 등록해야 한다.

1. 라즈베리파이의 `raspberry_voice_duck` 디렉터리에서 다음 명령을 실행한다. 32바이트 임의 키를 로컬 `.env`의 `DEVICE_TOKEN`에 기록하고 서버 등록용 SHA-256 해시만 출력한다. 다시 실행하면 키가 교체된다.

   ```sh
   python3 - <<'PY'
   import hashlib, os, secrets
   from dotenv import set_key

   os.umask(0o077)
   key = secrets.token_urlsafe(32)
   set_key(".env", "DEVICE_TOKEN", key)
   os.chmod(".env", 0o600)
   print(hashlib.sha256(key.encode("ascii")).hexdigest())
   PY
   ```

2. 새 백엔드 스키마가 적용된 후 `Ducky_was` 디렉터리에서 운영 DB에 등록한다. 예시의 시리얼, 계정 ID, 해시를 실제 값으로 바꾼다. 이 명령은 해당 시리얼의 기존 기기 키를 폐기하고 지정한 소유자만 활성화한다.

   ```sh
   docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' sh \
     -v device_serial=raspberry-duck-001 -v owner_id=123 -v token_hash=여기에_64자리_해시 < sql/provision-device.sql
   ```

3. Pi의 `.env`에서 `DEVICE_ID`를 등록한 시리얼로, `SERVER_BASE_URL`을 운영 HTTPS 주소로 설정한 뒤 음성 프로그램을 재시작한다. 소유자 계정으로 로그인하면 기기 목록에서 등록 결과를 확인할 수 있다.

기기 요청은 `Authorization: Bearer device.<DEVICE_TOKEN>`을 사용한다. 대화와 명령의 소유자는 인증된 연결에서 결정한다. 키를 폐기하려면 해당 기기와 계정의 `device_users.token_hash`를 `NULL`로 변경한다. 업데이트 전에 생성된 녹음 명령은 요청자를 확인할 수 없어 실행 목록에서 제외된다. 필요한 명령은 등록 후 웹에서 다시 요청한다.


## 녹음 명령 복구

웹의 녹음 요청은 기본 2분 동안 PENDING, 장치가 가져간 명령은 claimedAt 기준 기본 5분 동안 CLAIMED 상태를 유지한다. 다음 start-recording / commands/latest / commands/next 요청에서 기한을 지난 명령을 기존 FAILED 상태로 전환한다. 만료된 명령을 자동 재실행하지 않으며, 사용자가 다시 누르면 새 명령을 만든다. 마지막 명령이 CLAIMED 상태로 만료됐을 때 남아 있는 녹음·처리 중 장치 상태는 ERROR로 전환해 재시도를 허용한다.

DEVICE_COMMAND_PENDING_TIMEOUT 및 DEVICE_COMMAND_CLAIMED_TIMEOUT으로 ISO-8601 기간을 지정할 수 있다(기본 PT2M / PT5M). 장치의 녹음·STT·서버 응답·TTS 전체 최대 실행 시간보다 CLAIMED 기한을 길게 설정한다. 기한 만료는 실제 장치 프로세스를 중지하지 않으므로 장치가 뒤늦게 복귀해도 이전 명령의 성공 완료는 거부된다. 동일한 최종 결과의 완료 재전송은 기존처럼 멱등 처리한다.
