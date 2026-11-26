# Ducky WAS

**Java 17 · Spring Boot 4.0.6 · Spring Security · JPA · PostgreSQL / H2**

웹과 Raspberry Pi가 사용하는 API 서버입니다. 사용자 인증과 학습 유형, 세션과 메시지, 문서 검색과 기기 명령을 관리합니다.

> 채팅과 자료실 화면은 **[Ducky 웹 서비스 열기](https://ducklab.site/)**에서 확인할 수 있습니다.

[제품 소개](../README.md) · [웹](../Ducky_web/README.md) · [문서 처리기](../Ducky_rag/README.md)

## 팀원

| 팀원 | 역할 |
| --- | --- |
| [문경](https://github.com/mujang3) | 백엔드 · IoT |
| [은진](https://github.com/yim-eun-jin) | 백엔드 |
| 제환 | AI |

## 로컬 개발

문서 업로드와 웹 대화를 함께 확인하려면 [통합 개발 환경](../README.md#빠른-시작)을 실행하세요. 통합 실행은 `rag-local` 프로필에서 H2, Python HTTP 처리기, 문서 발췌 응답을 사용합니다.

백엔드 테스트는 `Ducky_was`에서 실행합니다.

```sh
./gradlew test
```

Windows PowerShell에서는 `.\gradlew.bat test`를 사용합니다. 백엔드와 PostgreSQL을 함께 띄우는 운영형 구성은 [compose.yaml](compose.yaml)과 [.env.example](.env.example)을 기준으로 설정합니다.

| 설정 | 용도 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 연결. Compose에서는 DB URL이 컨테이너 주소로 설정됩니다. |
| `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` | 사용자 JWT 서명과 웹 출처 허용 |
| `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL` | 모델 응답을 사용하는 환경 설정 |
| `DOCUMENT_MODE` | 문서 처리 연결 방식. `local` 또는 `cloud` |
| `DEVICE_COMMAND_PENDING_TIMEOUT`, `DEVICE_COMMAND_CLAIMED_TIMEOUT` | 녹음 요청과 장치 처리의 만료 시간. 기본 `PT2M`, `PT5M` |

<details>
<summary>PostgreSQL 수동 준비</summary>

PostgreSQL 17과 pgvector가 설치된 서버에서 역할과 DB를 준비합니다. 비밀번호는 개발 환경 값으로 교체합니다.

```sql
CREATE USER rubberduck WITH PASSWORD 'your-local-db-password';
CREATE DATABASE rubberduck OWNER rubberduck;
```

관리 권한으로 `rubberduck` 데이터베이스에 연결한 뒤 아래 명령을 실행합니다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

</details>

## API 둘러보기

| 경로 | 담당 기능 |
| --- | --- |
| `GET /api/health` | API 상태 확인 |
| `/api/auth/*`, `/api/users/me` | 로그인·회원가입·내 정보·학습 유형 |
| `/api/chat/conversations`, `/api/chat/messages`, `/api/chat/hints` | 대화 시작·재개·메시지 전송·힌트 |
| `/api/documents`, `POST /api/documents/search` | 자료 업로드·처리 상태·재시도·사용자별 검색 |
| `/api/devices`, `/api/devices/{deviceId}/commands/*` | 웹에서 기기 조회와 녹음 명령 요청 |
| `/api/duck/*`, `/api/iot/*` | Pi의 활성 세션 조회·대화·상태 보고·명령 수신 |

## 대화와 문서 처리

- **대화 라우팅**은 일반 답변, 단계별 힌트, 문서 검색, 재질문을 요청에 맞춰 선택합니다. [대화 하네스 계약](docs/CONVERSATION_HARNESS.md)
- **문서 검색**은 인증 사용자와 색인 완료 문서로 후보를 제한하고 BM25로 점수화합니다. 처리와 색인, 재시도 정책은 [AWS 문서 모드](docs/DOCUMENT_CLOUD.md)에서 확인할 수 있습니다.
- **운영 스키마**를 관리하는 경우 [수동 SQL](src/main/resources/db/manual)과 [인계 문서](../docs/DUCKY_1123_HANDOFF.md)를 참조하세요.

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

웹 요청은 `PENDING`, 장치가 가져간 명령은 `CLAIMED` 상태로 표시됩니다. 기한이 지난 요청은 다음 명령 조회·요청에서 `FAILED`로 전환됩니다. 웹에서 다시 요청하면 새 명령이 생성됩니다. 마지막 `CLAIMED` 명령이 만료되면 남아 있는 녹음·처리 상태를 `ERROR`로 전환해 재시도할 수 있게 합니다.

`CLAIMED` 만료 시간은 녹음·STT·서버 답변·TTS 전체 실행 시간보다 길게 설정합니다. 명령이 만료되어도 장치의 작업은 자체 종료까지 실행될 수 있습니다. 서버는 만료된 명령의 늦은 성공 완료를 거부하며, 같은 최종 결과의 완료 재전송은 멱등 처리합니다.
