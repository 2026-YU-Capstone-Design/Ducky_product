# Ducky 디버깅 학습 문서 처리

현재 구현은 **텍스트 PDF → 원본 보관 → Ducky 학습 문서 정규화 → 청킹 → 로컬 어휘 벡터 저장 → 사용자별 검색 → 웹 발췌 답변**이다. 외부 API와 AWS 없이 실행할 수 있다.

`normalization.py`가 코드·수식·표·짧은 기술 용어를 보존한다. 문서 ID와 사용자 권한은 Spring에서 관리하며, `DocumentProcessorClient`가 HTTP 처리기와 통신한다.

이 처리기는 러버덕 디버깅 학습에 사용하는 강의 자료·코드 예제·오류 로그·학습 노트를 검색 가능한 근거로 만든다. 재현 코드, 실제 오류, 기대 동작, 학습자의 확인 질문이 원문 문맥과 함께 남도록 처리한다. 질문에 바로 답할지 힌트로 유도할지는 `Ducky_was`의 대화 정책이 결정한다.

## 코드 구성

- `ducky_rag/pipeline.py`: 입력 형식·크기 검증, PDF 텍스트 변환, 청크와 처리 결과 생성.
- `ducky_rag/normalization.py`: 코드·스택 트레이스·수식·표 보존, 블록 ID·제목 경로·원문 줄 위치 관리.
- `ducky_rag/server.py`: Spring이 호출하는 내부 HTTP 처리기.
- `ducky_rag/pdf_layout.py`, `ducky_rag/chunking.py`: PDF 페이지 출처와 청크 문맥을 구성한다.
- `ducky_rag/aws_contract.py`, `ducky_rag/lambda_handler.py`: S3 이벤트와 SQS 배치, 처리 산출물과 완료 메시지 계약을 처리한다.
- `tests/`: 디버깅 학습 자료와 처리 실패 사례 검증.
- `scripts/smoke.py`: 실제 업로드·소유자별 검색·발췌 답변·근거 저장 통합 검증.

로컬 경로는 문서 변환, 검색과 발췌 답변을 제공한다. 기존 OpenAI Responses 답변 호출은 유지되며 `rag-local` 프로필에서는 호출하지 않는다. Lambda를 사용하는 AWS 경로는 실제 배포 환경에서 별도로 검증한다.

## 로컬 실행 (PowerShell, 저장소 루트)

Python 3.12, Java 17, Node/npm이 필요하다. 설치 명령은 최초 한 번 실행한다.

```powershell
py -3.12 -m venv .venv-rag
./.venv-rag/Scripts/python.exe -m pip install -r Ducky_rag/requirements-test.txt
```

첫 번째 터미널: PDF 처리기.

```powershell
$env:PYTHONPATH = 'Ducky_rag'
./.venv-rag/Scripts/python.exe -m ducky_rag.server --port 18091
```

두 번째 터미널: 백엔드. Java 17 설치 경로가 다르면 `JAVA_HOME`을 변경한다.

```powershell
cd Ducky_was
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
./gradlew.bat bootRun --args='--spring.profiles.active=rag-local --server.port=18080 --document.worker-url=http://127.0.0.1:18091 --app.cors.allowed-origins=http://127.0.0.1:13000'
```

세 번째 터미널: 웹.

```powershell
cd Ducky_web
npm.cmd ci
$env:NEXT_PUBLIC_API_BASE_URL = 'http://127.0.0.1:18080'
npm.cmd run dev -- --hostname 127.0.0.1 --port 13000
```

`http://127.0.0.1:13000`에서 회원가입·학습 유형 설정을 마친 뒤 설정의 자료실에 PDF를 업로드한다. `색인 완료` 후 대화에서 문서 내용을 질문한다. 채팅에 직접 첨부해도 업로드와 처리 완료를 기다린 후 질문이 전송된다. 답변에 `문서 발췌 모드 · 외부 AI 미사용`과 펼쳐 볼 수 있는 참고 문서가 표시된다.

DB는 `Ducky_was/data/rag-local.mv.db`, 원본·산출물은 `Ducky_was/data/documents/{userId}/{documentId}/`에 남는다. 운영 DB 및 기존 계정과 분리된다. 개발용 인증 비밀값을 사용하는 이 프로필은 루프백 주소에만 바인딩한다.

## Lambda 이미지와 개발 환경

Lambda 이미지는 `Ducky_rag` 디렉터리를 빌드 컨텍스트로 사용한다. `Dockerfile.lambda`는 Python 3.12 Lambda 런타임을 사용한다.

```bash
docker build --platform linux/amd64 -f Dockerfile.lambda -t ducky-document:dev .
```

`serverless.yml`은 함수와 로그 그룹을 정의한다. 문서 버킷, 요청·완료 큐, ECR, 역할과 이벤트 소스 매핑은 인프라에서 관리한다. dev 배포에는 해당 인프라 출력과 이미지의 불변 ECR digest를 사용한다. `RAG_PROCESSOR_REVISION`에는 빌드한 소스의 전체 Git SHA를 지정한다. Spring의 입력과 상태 전이는 [문서 클라우드 모드](../Ducky_was/docs/DOCUMENT_CLOUD.md)를 참고한다.

## 검증

백엔드와 처리기를 띄운 상태에서 저장소 루트에서 실행한다.

```powershell
$env:PYTHONPATH = 'Ducky_rag'
./.venv-rag/Scripts/python.exe -m pytest -p no:cacheprovider Ducky_rag/tests -q
./.venv-rag/Scripts/python.exe Ducky_rag/scripts/smoke.py --output .runtime/verification
```

`smoke.py`는 테스트 계정을 만들고, 실제 PDF 업로드·권한 격리·검색·답변·근거 저장·실패·재시도·삭제를 검사한다. 새 테스트 계정과 일부 실패 문서는 로컬 DB에 남는다. 운영 주소를 대상으로 실행하지 않는다.

## 데이터 계약

- 웹 → Spring: 기존 `Authorization: Bearer ducky.…`, `POST /api/documents`, multipart `file`. 사용자 ID는 인증 principal에서만 받는다.
- 업로드 응답: 기존 필드에 `processingError`, `processingWarnings`, `chunkCount`, `processingSeconds` 추가. 처리 대상 파일은 `indexingStatus=pending`, `status=queued`를 즉시 반환한다.
- 상태: `pending → processing → indexed | failed`. `GET /api/documents/{id}` 및 목록으로 조회. `POST /api/documents/{id}/retry`는 소유자의 실패 문서만 허용한다.
- Spring → 처리기: `POST /process?documentId=<서버 DB ID>&sourceType=pdf`, octet-stream 원본 바이트. 루프백 밖에서는 `RAG_WORKER_TOKEN` 필수. 브라우저가 직접 호출하지 않는다.
- 처리기 응답: `schemaVersion=ducky-document-v1`, `documentId`, `sourceSha256`, `markdown`, `canonicalBlocks`, `chunks`, `report`, `warnings`, `pageCount`, `processingSeconds`.
- 정규화 버전은 `ducky-learning-v2`, 청커는 `ducky-page-chunks-v2`, 블록 스키마는 `ducky-blocks-v1`이다. 문서 종류는 `learning_document`다. Markdown의 문서 ID는 서버 ID로 생성한다.
- 청크: `blockId`, `chunkKey`, `content`, `textForEmbedding`, `headingPath`, `blockType`, `previousBlockId`, `nextBlockId`. Markdown 표 행과 블록 수식은 유지하고, 긴 본문·코드는 공백과 기호를 변경하지 않고 분할한다. 코드 블록의 언어·들여쓰기·빈 줄을 보존한다.
- 저장: `source`, `document.md`, `canonical.jsonl`, `result.json`. 파일 이름을 저장 경로로 사용하지 않는다. DB에 청크 내용·출처·벡터 모델을 저장한다.
- 벡터: `lexical-ko-en-v1`, 단어 및 한국어 2글자 특징의 정규화 sparse map. 고정 차원의 dense 벡터가 아니다. 기존 64차원 해시 청크는 호환 읽기를 유지한다.
- 검색: `POST /api/documents/search`, `{query, limit, documentIds?}`. 모든 후보를 로그인 사용자 및 완료 상태로 제한한다. ID 범위를 지정하면 각 ID의 소유권도 검사한다. 빈 ID 목록은 검색 결과 없음이다.
- 답변: 기존 `/api/chat/messages`. 참고한 문서 ID·청크 ID·블록 ID·본문을 대화 메시지에 저장하고 `sources[]`로 반환한다. 문서를 지워도 기존 대화의 인용 기록은 유지된다.
- 중복·경쟁: DB 행 잠금과 처리 시도 토큰을 사용한다. 이미 완료된 작업은 다시 적재하지 않고, 변환 도중 삭제된 문서는 되살리지 않는다. SHA-256/문서 ID/스키마가 다른 결과는 실패 처리한다. 프로세스 중단 후 유효기간이 지난 작업은 실패로 바꾸고 재시도할 수 있게 한다.

## 현재 한계

- 20MiB/200페이지/10,000청크 상한이다. 상한 초과를 조용히 잘라서 성공 처리하지 않는다.
- 이미지뿐인 페이지가 있으면 `OCR_REQUIRED`. 텍스트와 이미지가 함께 있으면 텍스트만 처리하고 `IMAGES_NOT_ENRICHED`를 표시한다. 스캔 PDF의 실제 OCR은 검증하지 않았다.
- PDF/TXT/MD/CSV/JSON이 새 경로다. DOCX/PPTX는 기존 Spring 추출 경로를 유지한다.
- TXT/CSV/JSON은 원문을 보존하며 Markdown 제목으로 재해석하지 않는다. 현재 표 행 구조화 대상은 Markdown 표다. HTML 표는 본문으로 보존하며 셀별 구조화는 추가 검증이 필요하다.
- 정규화 변경 전에 이미 저장된 청크는 자동으로 바뀌지 않는다. 새 정책으로 처리하려면 로컬 검증 문서를 다시 업로드해야 한다. 운영 도입 전 일괄 재색인 경로는 별도 작업이다.
- 로컬 검색은 소유자 청크를 메모리에서 점수화한다. pgvector 인덱스, BM25/hybrid, 대규모 자료 검색, 의미 검색 품질은 구현·검증 대상에 남아 있다.
- 발췌 모드는 LLM이 아니다. 자료가 없으면 근거를 찾지 못했다고 안내한다. 일반 지식 답변 품질과 실제 OpenAI 호출은 이번 검증 범위가 아니다.
- 파일 저장과 DB는 서로 다른 저장소이므로 운영 도입 전 장애 시 orphan artifact 정리와 스키마 이전 절차가 필요하다.

로컬 검증 산출물은 `.runtime/verification/`에 저장하며 Git에는 포함하지 않는다.
