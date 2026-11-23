# Ducky 11/23 작업 결과 및 다른 로컬 인계

작성 기준: 2026-11-23 KST. 기능 동결 11/23, 발표 11/24.

이 문서는 Git으로 전달 가능한 작업 결과와 재개 절차다. `ORCA_TEAM_STATUS.md`, 워커 상태 파일, `.runtime/`, 개인 로그, 인증정보, 실제 PDF와 DB는 포함하지 않는다. 과거 검증 결과와 현재 실물 확인을 구분한다.

## 1. 핵심 상태와 승인 범위

- 제품 구현·통합·기존 CI·로컬 브라우저 검증은 완료했다. 실제 LLM 및 실제 AWS 문서 처리 종단 검증은 미완료다.
- 제품의 문서 cloud 경로와 인프라 정의가 존재한다는 사실은 AWS에 적용됐다는 뜻이 아니다.
- 사용자 제공 학교 CloudShell 조회에서 계정 `300390308343`, 역할 `AccountFullAccessRole`을 확인했다. `ap-southeast-2`의 이름 조건 없는 Lambda/ECR 목록은 각각 `[]`였다. SQS 출력 본문·종료코드는 아직 확인 필요다. 다른 계정·리전은 미조사다.
- Lambda 공식 릴리스 SHA, ECR image digest, 함수 ARN과 실제 인프라 연결값은 확보하지 못했다. 원본 `0f8ecc7`은 공식 Lambda 릴리스 SHA가 아니다.
- 최신 사용자가 이 인계 문서의 커밋·push와 기존 제품 PR #19/인프라 PR #1 병합을 명시적으로 요청했다. 종전의 PR 병합 금지는 이 두 PR에 대해 해제됐다.
- Terraform apply/import, 운영 배포, EC2 instance profile 전환, 운영 DB 변경·전환, DNS 변경과 원본 미커밋 파일 수정은 여전히 승인 범위 밖이다. `main` 병합·publish workflow 수동 실행도 하지 않는다.
- 인프라 PR #1은 2026-11-23에 `feat/operations-monitoring`으로 병합됐다. merge SHA는 `152c02e863b8689288ba5711d851b05d4b3046d4`다. AWS apply는 실행되지 않았다.
- 제품 인계 대상은 아래 PR #19이며, 이 문서는 그 통합 브랜치에 추가된다. 최종 제품 merge SHA/CI는 아래 원격 조회 명령으로 확인한다. 새 PR로 동일 구현을 중복 제출하지 않는다.

## 2. 저장소·PR·확정 코드

| 구분 | 저장소 / PR | 기준 및 확정 코드 |
| --- | --- | --- |
| 제품 | [Ducky_product](https://github.com/2026-YU-Capstone-Design/Ducky_product), [PR #19](https://github.com/2026-YU-Capstone-Design/Ducky_product/pull/19) | base `develop`, 원래 기준 `caee492962a9352cd80a9de0d38799aba9c9c2e2` |
| 제품 구현 최종 | `feat/ducky-integration-1123` | 문서 추가 전 코드 `0fb4a9cbe50ec5fd33a5f7f0a2cc9369b391fc8c`; 이 문서 추가 커밋은 이후에 위치 |
| 인프라 | [Ducky_infra](https://github.com/2026-YU-Capstone-Design/Ducky_infra), [PR #1](https://github.com/2026-YU-Capstone-Design/Ducky_infra/pull/1) | base `feat/operations-monitoring`, 원래 기준 `840b0b45f28e0897eae1c90d8b28641092b6aba6` |
| 인프라 구현 최종 | `feat/ducky-document-infra-1123` | `d5fb84d56dec562d059d133afd6f8067e4dd465c`, 병합 후 base `152c02e863b8689288ba5711d851b05d4b3046d4` |
| 프론트 워커 | `feat/ducky-frontend-1123` | `3931b51790cd6bb86f7264ac6ae781063c59003a` |
| 대화 하네스 워커 | `feat/ducky-harness-1123` | `0b8b1c409f426d93fb14294c4e630729f52a73e2` |
| 문서 백엔드 워커 | `feat/ducky-documents-1123` | `3425cc5011aa77cd725adbc3c51a8be0af9eddb5` |
| 원본 제품 선행 | `feat/document-lambda-pipeline` | `0f8ecc7961653a81e05f2e6a2a1f08160ac84e5f`; BM25/청킹 선행은 원격에 정상 push됐지만 PR #19에는 미포함 |
| 원본 RDS | `feat/rds-foundation` | `840b0b45f28e0897eae1c90d8b28641092b6aba6`; 이 SHA만 원격에 있고 RDS 미커밋 구현은 없음 |
| 비공개 로컬 검증 | `verify/ducky-1123-with-prereqs` | `090be69bbdb7b02499c7bb6667bfb46df9c64c8e`; 원격 push/제품 PR 병합 금지 |

제품 PR에는 선행 `bca4ce9`/`0f8ecc7` 및 원본 미커밋 Lambda를 포함하지 않았다. 워커 커밋은 통합 과정의 cherry-pick·메시지 정리로 제품 통합 SHA와 다를 수 있다. 소스와 파일을 비교하지 않고 SHA 차이만으로 통합 누락을 판단하지 않는다.

다른 로컬에서 현재 원격 상태 확인:

```powershell
gh pr view 19 --repo 2026-YU-Capstone-Design/Ducky_product --json state,baseRefName,headRefOid,mergeCommit,mergedAt,url
gh pr checks 19 --repo 2026-YU-Capstone-Design/Ducky_product
gh pr view 1 --repo 2026-YU-Capstone-Design/Ducky_infra --json state,baseRefName,headRefOid,mergeCommit,mergedAt,url
gh pr checks 1 --repo 2026-YU-Capstone-Design/Ducky_infra
```

## 3. 구현한 내용

### 프론트

- 변환 완료와 색인 완료를 구분한다. `processed`만으로 검색 가능이라고 표시하지 않고 `indexed`와 `ragEnabled`를 확인한다.
- 처리 상태 폴링, 실패 안내, 서버 `retryAllowed`에 따른 재시도, 전송 실패 시 입력 보존을 연결했다.
- PDF 출처의 optional `pageNumber`와 구형 응답을 함께 처리한다. 쪽 필드가 없을 때 임의 쪽 번호를 만들지 않는다.
- `responseStrategy`, 출처, 힌트 상태와 선택 문서 범위 복원을 연결했다.
- 계정 전환 시 요청·응답·메모리·서비스워커 캐시가 이전 사용자 자료를 남기지 않도록 보완했다.

### 대화 하네스

- 일반 직접 답변(DIRECT), 문서 질문(DOCUMENT), 명시적 힌트(HINT), 모호한 요청의 확인 질문(CLARIFY)을 구분한다.
- 이번 요청의 nonempty `documentIds`는 명시적 문서 문맥이다. 새 PDF 첨부 뒤 `Quasar retry budget`처럼 문서 키워드 없는 질문도 해당 허용 문서를 검색한다.
- 다음 턴에 선택을 생략하고 일반 질문을 하면 DIRECT와 빈 출처를 반환한다. 이후 문서 질문은 대화에 저장된 범위를 재사용한다.
- `documentIds` 생략은 저장 범위 유지, 빈 배열은 선택 해제, nonempty 배열은 새 범위 지정이다. 저장 범위를 영구 DOCUMENT 강제로 사용하지 않는다.
- 대화의 `selected_document_ids_json`, 메시지의 `response_strategy`와 출처를 저장한다. 타인 문서·대화 접근을 차단한다.
- 비어 있지 않은 저관련 lexical 후보도 정답 근거로 확정하지 않는다. 로컬 발췌/규칙 모드에서 근거 충족 미판정 안내를 명확히 표시한다. 실제 LLM 의미 판단 품질까지 검증한 것은 아니다.
- 후속 수정 `18d1b10`, `bdfe52c`, `0b8b1c4`의 동작이 제품과 private verify에 포함됐음을 최종 검토했다.

### 문서 백엔드

- local HTTP 처리와 cloud S3/SQS 처리를 분리했다. 기본값은 `DOCUMENT_MODE=local`이다.
- cloud 업로드 시 사용자·문서·attempt·원본 hash/key/size를 먼저 저장하고 업로드한 정확한 S3 version을 추적한다.
- 완료 메시지의 manifest/jobId/revision, 원본 정보, 산출물 해시·길이·개수와 소유권을 검증한다.
- 청크 ID·원문 블록 참조·PDF 쪽·필드 타입을 검증하고 트랜잭션으로 색인을 교체한다. 이전 attempt·중복·삭제 후 완료로 자료를 되살리지 않는다.
- 실행 모드별 재시도 API 허용 조건과 응답 `retryAllowed`를 일치시켰다.
- 원본 미커밋 Lambda 사본의 실제 Python producer가 만든 합성 TXT/PDF 결과를 Java가 소비하는 교차언어 검증을 준비했다.

### 릴리스 준비 보완

- Compose가 cloud 필수 설정 7개를 backend 컨테이너에 명시 전달하도록 수정했다. 호스트 `.env`만으로 임의 변수가 컨테이너에 전달되지는 않는다.
- 문서 SQL과 별도로 대화 선택 범위/응답 전략용 반복 적용 가능한 수동 SQL을 추가했다.
- 기존 데이터 보존, 2회 SQL 적용, Hibernate 전체 schema validate와 새 필드 round-trip 회귀를 추가했다.
- CI에 격리 PostgreSQL 17 schema 테스트와 컨테이너를 띄우지 않는 Compose 렌더링 회귀를 추가했다.

### 인프라

- 기존 `operations` 소유 문서 버킷 주소를 사용하고 요청/완료 표준 SQS와 각각의 DLQ, 제한된 큐 정책, `originals/` 알림을 정의했다.
- Lambda 실행 역할, CloudFormation 배포 역할, ECR, 별도 배포 버킷과 선택적 SQS 매핑을 정의했다.
- `document_pipeline=null`이 기본값이며 `enable_mapping=false`로 시작한다. Lambda ARN 없이 가짜 매핑을 만들지 않는다.
- Lambda 함수·버전·로그 그룹은 제품 Serverless 소유이며 Terraform이 중복 생성하지 않는다.
- 공유 `DuckyEC2SSMRole`에 새 문서 권한을 부여하지 않는 가드를 추가했다. backend 전용 역할/profile 전환은 별도 검토·승인 대상이다.
- AWS 자격증명 없는 fmt/validate/mock CI를 추가했다. 실제 AWS 적용이나 운영 권한 검증은 수행하지 않았다.

## 4. 검증 결과와 한계

| 검증 | 확인 결과 | 범위/한계 |
| --- | --- | --- |
| 제품 구현 `0fb4a9c` CI | [44557841412](https://github.com/2026-YU-Capstone-Design/Ducky_product/actions/runs/44557841412) 3 jobs 성공 | backend/PostgreSQL schema, frontend lint/test/build, Python 단위 테스트. 이 문서 추가 후의 새 CI는 PR checks 확인 |
| 인프라 `d5fb84d` CI | [44473728314](https://github.com/2026-YU-Capstone-Design/Ducky_infra/actions/runs/44473728314), push 44473720720 성공 | fmt, 4 root validate, mock 15개. AWS API/apply 없음 |
| 최신 릴리스 보완 로컬 backend | 총 111, 실패 0, 오류 0, skip 1 | 조건부 Python producer 테스트에 snapshot/image 입력이 없어 skip. 111개 전부 성공으로 표현하지 않음 |
| 이전 제품/private verify 검증 | 각각 총 111, 실패·오류·skip 0 | 11/19 당시 owner snapshot/image를 제공한 로컬 결과. 이후 모든 실행에 동일 적용하지 않음 |
| 문서 워커 최종 회귀 | H2 89, PostgreSQL 54 통과 | 합성 산출물·로컬 double 및 실제 Python producer 포함. 운영 AWS 아님 |
| 하네스 워커 최종 회귀 | 52개 통과 | 선택 문맥/일반 의도 분리·범위 재사용·빈배열·타인·저관련 후보 포함 |
| 실제 로컬 브라우저 | 첨부/문서 질문/일반 전환/힌트/확인 질문/복원/타인 격리 확인 | API 성공만으로 브라우저 성공이라 하지 않음. 실제 AWS/LLM 아님 |
| 유효 PDF 복구 | 본인 로컬 처리기 일시 장애 → 복구 → 같은 owner 재시도 → indexed → 질문/출처 성공 | 통제된 로컬 장애 주입. 손상 PDF 재시도 후 재실패 사례와 구분 |
| 실제 LLM | 미검증 | 마지막 로컬 점검에서 승인된 키를 찾지 못함. 새 로컬에서는 키의 존재만 확인하고 값은 출력하지 않음 |
| 실제 AWS 문서 종단 | 미검증 | 대상 리전 Lambda/ECR 빈 목록, 공식 SHA/digest/ARN과 실물 연결값 미확보 |
| dense/RRF/hybrid | 미구현·미평가 | BM25나 lexical 결과를 이 방식의 결과로 보고하지 않음 |

실제 브라우저의 새 2쪽 합성 PDF 흐름은 private verify backend `090be69`와 제품 frontend `fcc9e58` 조합에서 확인했다. 새 첨부 질문 → DOCUMENT/PDF 1쪽 → 다음 Python 일반 질문 DIRECT/빈 출처 → 문서 후속 질문 저장 범위 재사용 → 새로고침 출처/힌트 복원 → 다른 사용자 자료 0건과 근거부족/출처없음을 확인했다. PDF 쪽 생성 성공을 선행 제외 제품 PR 단독의 성공으로 확대하지 않는다.

이 인계 문서 추가만을 위해 기존 브라우저·고정 질문·전체 로컬 회귀를 불필요하게 반복하지 않는다. 새 commit의 원격 CI는 별도로 종료를 확인한다. 과거 PID·포트 기록은 현재 프로세스가 살아 있다는 증거가 아니다.

## 5. 고정 20질문과 57쪽 PDF

답이 있는 18문항과 자료 밖 2문항을 그대로 유지했다. 문항을 바꾸거나 점수 임계값을 고정 질문에 맞춰 조정하지 않는다.

| 대상 | 이전 결과 / 최종 동일 결과 |
| --- | --- |
| 제품 단독 lexical Hit@5 | 7/18 |
| 선행 포함 verify lexical Hit@5 | 10/18 |
| 선행 포함 verify BM25 Hit@5 | 15/18 |

이는 검색 지표이며 20/20 답변 정답이나 LLM 품질을 뜻하지 않는다. BM25와 청킹 변화가 함께 있어 BM25만의 효과로 단정하지 않는다. 한국어 1개·이미지 기반 2개 문항 누락 한계가 남는다.

기존 로컬 평가 입력은 Git으로 배포하지 않는다. 권한 있는 별도 경로로 동일 파일을 전달받고 아래 SHA256을 비교한다.

| 입력 | 기존 로컬 위치 | SHA256 |
| --- | --- | --- |
| 57쪽 PDF | 원본 제품 루트 `Chapter 2 Synchronization (동기화)-1.pdf` | `4d201f29514a6e9ef8725f52acf9fa0b1a64fa6c15643a7b86977dd295133e9d` |
| 고정 20질문 | `.local-verification/eval/synchronization-v1.json` | `db92732c61a993a2fec77da8b915315fb89f02dd5fe0a7f5591ffb1bd10db349` |
| 기존 evaluator | `.local-verification/scripts/evaluate_retrieval.py` | `8094eaf44a42f34dd907cb790236e7909e0a23ad5584a577252ac7422e579046` |

위 평가 도구·입력이 없으면 새 로컬에서 고정20 재현은 미준비다. 빈 대체 PDF나 새 질문으로 기존 회귀 통과를 주장하지 않는다. 기존 `.runtime/integration-1123/verify_fixed20.py`는 옛 절대경로를 참조하므로 파일 전달 후 새 로컬 경로를 검토해야 한다.

## 6. 다른 로컬로 가져오는 순서

### 6.1 Git으로 가져올 수 있는 코드

Git, Java 17, Node 24, Python 3.12, GitHub CLI가 필요하다. Terraform 검증은 1.17.2 기준이며 컨테이너 테스트에는 별도 Docker 환경이 필요하다. 아래 clone은 기존 원본 폴더를 덮어쓰지 않는 새 작업 디렉터리에서 실행한다.

```powershell
git clone --branch develop https://github.com/2026-YU-Capstone-Design/Ducky_product.git
git clone --branch feat/operations-monitoring https://github.com/2026-YU-Capstone-Design/Ducky_infra.git
```

제품 PR #19가 아직 미병합이라면 기본 clone의 `develop`에 통합 내용이 없을 수 있다. 원격 PR 상태를 먼저 확인하고 검토에는 `feat/ducky-integration-1123`를 명시한다. 인프라 PR #1 병합 대상은 `main`이 아닌 `feat/operations-monitoring`이다.

### 6.2 clone으로 옮겨지지 않는 것

- 제품 원본의 Lambda 미커밋 10파일과 인프라 원본의 RDS 미커밋 10파일.
- 비공개 `verify/ducky-1123-with-prereqs`와 로컬 테스트 이미지.
- 고정20 PDF·질문·평가 도구, 개인 계획/계약 스냅샷, `.runtime/` 증거·브라우저 스크린샷.
- `.env`, 인증정보, 로컬 DB·문서 저장소와 사용자 세션.

**전체 이전이 끝난 것으로 생각하고 기존 PC를 초기화하거나 원본을 삭제하면 안 된다.** 미커밋 구현은 각 소유자가 검토한 공식 릴리스 또는 명시적으로 허용된 별도 안전 인계로 전달해야 한다. 이 작업에서 원본을 commit/stash/reset/checkout하거나 파일을 이동하지 않았다.

private verify가 꼭 필요하면 소유자의 기존 PC에서 외부 로컬 경로에 `git bundle create <외부경로>/ducky-verify.bundle verify/ducky-1123-with-prereqs`로 별도 인계할 수 있다. 이는 선택적 후속 절차이며 이 작업에서 새 bundle을 만들거나 원격에 올리지는 않았다. 새 로컬에서도 해당 브랜치를 제품 base로 병합/push하지 않는다.

### 6.3 제품 로컬 실행

2026-11-24 추가한 개발 실행기를 사용하는 경우 Java 17, Node.js 24, Python 3.12 설치 후 저장소 루트에서 `npm run setup:local`을 한 번 실행하고 매번 `npm run dev`로 시작한다. 세 서비스를 자동 연결하며 DB·업로드는 `.runtime/local-dev/<API포트>/`에 격리한다. 사용 중인 포트는 건드리지 않고 `Ctrl+C`로 직접 시작한 서비스만 정리한다. 상세 사용법은 [루트 README](../README.md)를 참고한다. 아래는 서비스별 수동 실행 대안이며 실행기와 동시에 같은 포트로 시작하지 않는다.

새 clone의 제품 루트에서 설치한다. Windows의 `python`이 Store 별칭일 수 있으므로 `py -3.12` 또는 가상환경 실행 파일을 사용한다.

```powershell
py -3.12 -m venv .venv-rag
./.venv-rag/Scripts/python.exe -m pip install -r Ducky_rag/requirements-test.txt
```

먼저 18091/18080/13000이 비어 있는지 확인한다. 다른 사용자의 프로세스를 종료하지 않는다.

```powershell
Get-NetTCPConnection -State Listen -LocalPort 18091,18080,13000 -ErrorAction SilentlyContinue
```

터미널 1, 제품 루트:

```powershell
$env:PYTHONPATH = 'Ducky_rag'
./.venv-rag/Scripts/python.exe -m ducky_rag.server --port 18091
```

터미널 2, `Ducky_was`:

```powershell
# JAVA_HOME은 이 PC의 Java 17 실제 설치 경로로 지정한다.
./gradlew.bat bootRun --args='--spring.profiles.active=rag-local --server.port=18080 --document.worker-url=http://127.0.0.1:18091 --app.cors.allowed-origins=http://127.0.0.1:13000'
```

터미널 3, `Ducky_web`:

```powershell
npm.cmd ci
$env:NEXT_PUBLIC_API_BASE_URL = 'http://127.0.0.1:18080'
npm.cmd run dev -- --hostname 127.0.0.1 --port 13000
```

브라우저 `http://127.0.0.1:13000`에서 합성 로컬 계정을 사용한다. `rag-local`은 외부 AI를 호출하지 않는 로컬 발췌 모드이며 새 clone의 `Ducky_was/data/rag-local.mv.db`와 `data/documents/`를 사용한다. 운영 DB/문서/계정을 연결하지 않는다. 이 프로필의 개발용 인증 설정은 운영에 쓰지 않는다.

### 6.4 필요한 검증 명령

```powershell
# 제품 루트: Python
./.venv-rag/Scripts/python.exe -m pytest -p no:cacheprovider Ducky_rag/tests -q

# Ducky_was: Java 및 Compose 렌더링. 렌더링에는 Docker Compose CLI 필요.
./gradlew.bat clean build --no-daemon
py -3.12 -m unittest discover -s tests -p 'test_compose_configuration.py' -v

# Ducky_web
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

`DOCUMENT_LAMBDA_SNAPSHOT`와 `DOCUMENT_LAMBDA_IMAGE`가 없는 producer 테스트는 skip될 수 있다. 실제 snapshot/image를 전달받지 않았으면 skip을 성공으로 세지 않는다. 로컬 이미지의 hash는 ECR 공식 digest를 대신하지 않는다.

인프라 저장소 루트, AWS 자격증명 없이 수행하는 검증:

```powershell
terraform fmt -check -recursive
terraform -chdir=operations init -backend=false -input=false -lockfile=readonly
terraform -chdir=operations validate
terraform -chdir=operations test
terraform -chdir=operations/modules/document-pipeline init -backend=false -input=false -lockfile=readonly
terraform -chdir=operations/modules/document-pipeline validate
terraform -chdir=operations/modules/document-pipeline test
```

CI는 adoption/bootstrap을 포함한 4 root를 검증한다. mock 테스트의 `command=apply`는 합성 provider 동작이며 실제 AWS apply가 아니다. 원격 state 연결/조회·import·plan·apply는 이 로컬 검증 명령에 포함하지 않는다.

## 7. AWS Console에서 현재 확인할 것

학교 계정으로 로그인하고 우측 상단 리전을 시드니 `ap-southeast-2`로 맞춘다. 역할 이름으로 전체 권한을 확정하지 않는다. 새 키·새 계정을 만들지 않는다.

| 서비스 | 콘솔 경로 | 읽기 확인 내용 |
| --- | --- | --- |
| SQS | 대기열 / Queues | 검색어를 지우고 전체 목록. 비어 있으면 화면의 대기열 없음 확인. 메시지 수신/삭제는 하지 않음 |
| CloudFormation | 스택 / Stacks | 상태 필터를 넓혀 문서 관련 stack 존재·상태·Outputs 확인. 템플릿/파라미터 전체 공유 불필요 |
| S3 | 범용 버킷 → 후보 버킷 → 속성/권한 | 문서용 후보의 리전·versioning·암호화·public block·기존 event notifications. 실제 객체 내용은 읽지 않음 |
| ECR | 프라이빗 레지스트리 → 리포지토리 | 이후 공식 저장소가 생기면 image digest/platform 확인 |
| Lambda | 함수 → 대상 함수 → 코드/구성 | 이후 ARN·이미지·role·timeout·memory·동시성·허용된 revision 하나만 확인. 전체 환경변수 출력 금지 |
| EC2 | 인스턴스 → backend → 보안 | 현재 IAM 역할과 SSM 접속 상태 확인. profile 교체는 별도 승인 |

지금까지 받은 실물 메타데이터:

- 이름 조건 없는 Lambda/ECR 조회: 각각 `[]`.
- SQS 이름 조건 제거 조회: 출력 없음, 종료코드 미제공. 빈 목록으로 확정하지 않는다.
- S3의 `ducky` 이름 조건: `ducky-demo-backups`, `ducky-tfstate-300390308343-ap-southeast-2`만 확인. 실제 용도/소유 state는 이름만으로 증명되지 않는다.
- IAM의 `ducky`/`Ducky` 이름 조건: `DuckyEC2SSMRole`, `DuckyFrontendDeployRole`만 확인. 정책 내용은 미검증.

CloudShell에서 SQS만 명시적으로 확인하려면:

```bash
aws sqs list-queues --region ap-southeast-2 --no-paginate --query '{QueueUrls:QueueUrls}' --output json --no-cli-pager
echo "exit_code=$?"
```

출력 null/빈 배열과 종료코드 0을 함께 확인한다. 다른 리전·다른 계정으로 임의 확대하거나 default 로컬 AWS profile을 사용하지 않는다.

## 8. 실제 AWS 연결을 위한 남은 순서

이 절은 후속 실행 계획이며 실제 배포 완료 보고가 아니다. 콘솔 수동 생성과 기존 Terraform/Serverless 소유권을 중복시키지 않는다.

1. **Lambda 담당자 공식화:** 원본 미커밋 구현의 검토·테스트·공식 SHA/PR을 담당자가 확정한다. baseline `0f8ecc7`을 대신 사용하지 않는다. 빌드 대상 `linux/amd64`와 실제 이미지 소스를 연결한다.
2. **인프라 소유권 확인:** 계정/리전/stage, 기존 operations state, 문서·배포·state·backup 버킷, KMS/정책·알림 소유권을 검토한다. stage는 아직 확정되지 않았고 `dev`는 설정 기본/예시일 뿐이다.
3. **변경 계획 검토 후 별도 승인된 적용:** 문서 버킷, 요청/완료 표준 큐·DLQ, IAM, ECR, 배포 버킷을 기존 코드로 준비한다. 초기 `enable_mapping=false`, function ARN 생략. 기존 bucket notification 전체를 덮어쓰지 않는다.
4. **공식 이미지:** 담당자가 공식 SHA로 빌드하고 ECR에 push해 `repository@sha256:digest`를 확정한다. `latest`나 로컬 Docker image ID로 공식 배포를 식별하지 않는다.
5. **Serverless 함수 배포:** 제품 `serverless.yml`로 함수/버전/로그를 관리한다. 공식 SHA/digest/revision/ProcessorArn을 한 묶음으로 인계한다. 현재 제품 clone에는 원본 미커밋 `serverless.yml`이 없으므로 이 단계를 바로 실행할 수 없다.
6. **함수 확인 후 요청 큐 매핑:** timeout 300초, memory 2048MB, x86_64, reserved concurrency 2, 실행 역할을 확인한 뒤 정확한 ARN과 `enable_mapping=true`를 설정한다. 요청 큐 batch 1/window 0/max concurrency 2/`ReportBatchItemFailures`; visibility 1800초, maxReceiveCount 5.
7. **S3 알림:** 문서 버킷의 `originals/` ObjectCreated만 요청 표준 SQS로 연결한다. `markdown/` 결과가 재트리거되지 않도록 한다. 큐 정책에 bucket SourceArn과 account 제약을 둔다.
8. **backend 역할/실행 설정:** 공유 역할과 분리된 backend 전용 role/profile을 검토한다. SSM/기존 서비스 권한을 보존하고 컨테이너 IMDS/credential chain을 확인한다. 실제 전환은 별도 승인 대상이다.
9. **DB 준비:** 아래 두 수동 SQL을 기존 DB 구조·백업·복원 계획과 함께 검토한다. 테스트 DB에서 반복 적용/기존 데이터 보존/validate를 확인하고 운영 적용은 DB 담당 절차로 진행한다. RDS 이전 자체는 별도 작업이다.
10. **실제 합성 문서 검증:** 승인된 환경에서 동일 로그인으로 PDF 업로드 → indexed → 문서 질문/문서·쪽 출처 → 일반/힌트/모호함/근거부족 → 통제된 일시 장애 복구 → 새로고침 → 타인 격리를 검증한다. 실제 LLM은 별도 환경/결과로 구분한다.

인프라 담당자에게 받을 값:

```text
account / region / stage
DOCUMENT_BUCKET
DOCUMENT_REQUEST_QUEUE_ARN
DOCUMENT_COMPLETION_QUEUE_URL
DOCUMENT_LAMBDA_ROLE_ARN
DOCUMENT_DEPLOYMENT_ROLE_ARN
SERVERLESS_DEPLOYMENT_BUCKET
ecr_repository_url
function_arn / mapping_enabled
backend 전용 role/profile 및 SSM/IMDS 검토 결과
operations state / bucket / notification 소유권 확인 결과
```

Lambda 담당자에게 받을 값:

```text
공식 release SHA / PR
RAG_PROCESSOR_REVISION
ECR repository@sha256:digest 및 실제 platform
ProcessorArn / account / region / stage
normalizer / chunker / result / block / manifest / completion schema 버전
```

작업 사본의 계약 버전은 normalizer `ducky-learning-v2`, chunker `ducky-page-chunks-v2`, result `ducky-document-v1`, block `ducky-blocks-v1`, manifest `ducky-document-manifest-v1`, completion `ducky-document-completion-v1`이었다. 배포 이미지와 일치하는지는 아직 미검증이다.

## 9. backend cloud 설정과 SQL

운영 Compose가 전달하는 7개 설정:

```dotenv
DOCUMENT_MODE=cloud
DOCUMENT_REGION=ap-southeast-2
DOCUMENT_BUCKET=<검증된 문서 버킷>
DOCUMENT_COMPLETION_QUEUE_URL=<검증된 완료 큐 URL>
RAG_PROCESSOR_REVISION=<공식 릴리스 식별자>
DOCUMENT_AWS_CREDENTIALS=instance-role
DOCUMENT_COMPLETION_POLL_ENABLED=true
```

이 템플릿은 실물 설정이 아니다. 계정 키를 저장소/.env.example에 넣지 않는다. 로컬 profile/emulator는 별도 설정이며 운영 Compose는 개발자 profile mount나 emulator endpoint를 자동 전달하지 않는다.

검토할 SQL:

1. `Ducky_was/src/main/resources/db/manual/20261119_document_cloud.sql`: 문서 source/attempt/revision 필드와 `document_upload_attempts`.
2. `Ducky_was/src/main/resources/db/manual/20261120_conversation_harness.sql`: `conversations.selected_document_ids_json`, `messages.response_strategy`.

운영에 두 SQL을 실행하거나 `ddl-auto=update`로 자동 변경하지 않았다. migration 적용과 RDS 전환은 동일 작업이 아니다. 기존 PostgreSQL을 유지하는 경우에도 cloud 문서 연결은 가능하며 RDS가 문서 처리의 필수 선행이라고 단정하지 않는다.

제품 publish workflow는 `main` push에서 이미지를 발행하고 프론트 deploy 태그를 갱신한다. `develop` 병합은 이 publish 조건에 해당하지 않는다. backend/Lambda가 자동으로 운영 반영됐다고 가정하지 않는다. 운영 배포 금지 상태에서는 `main` 병합이나 publish workflow 수동 실행을 하지 않는다.

## 10. 원본·작업공간·비공개 자료 보존

기존 경로:

| 구분 | 경로 |
| --- | --- |
| 원본 제품 | `C:/Users/E/Desktop/Ducky_product` |
| 원본 인프라 | `C:/Users/E/Desktop/Ducky_infra` |
| 팀장 통합 | `C:/Users/E/orca/workspaces/Ducky_product/ducky-integration-1123` |
| 프론트 | `C:/Users/E/orca/workspaces/Ducky_product/ducky-frontend-1123` |
| 하네스 | `C:/Users/E/orca/workspaces/Ducky_product/ducky-harness-1123` |
| 문서 백엔드 | `C:/Users/E/orca/workspaces/Ducky_product/ducky-documents-1123` |
| 문서 인프라 | `C:/Users/E/orca/workspaces/Ducky_infra/ducky-document-infra-1123` |
| private verify | `C:/Users/E/orca/workspaces/Ducky_product/ducky-1123-with-prereqs` |

원본 제품에서 보존한 Lambda 10파일:

```text
Ducky_rag/.dockerignore
Ducky_rag/README.md
Ducky_rag/ducky_rag/pipeline.py
Ducky_rag/.gitignore
Ducky_rag/AWS_PIPELINE.md
Ducky_rag/Dockerfile.lambda
Ducky_rag/ducky_rag/aws_contract.py
Ducky_rag/ducky_rag/lambda_handler.py
Ducky_rag/requirements-lambda.txt
Ducky_rag/serverless.yml
```

원본 인프라에서 보존한 RDS 10파일:

```text
README.md
database/.terraform.lock.hcl
database/README.md
database/backend.tf
database/backend.tfbackend.example
database/main.tf
database/outputs.tf
database/terraform.tfvars.example
database/variables.tf
database/versions.tf
```

이 파일들의 내용·index·원본 HEAD를 이번 인계 전후 해시로 비교한다. 원본의 변경을 이번 문서 커밋에 포함하지 않는다. `.runtime/integration-1123/corrections/`의 증거 JSON은 로컬 제외 경로에만 저장한다.

공통 계약 스냅샷은 원본의 계속 갱신되는 파일과 구별한다. 기존 위치는 `C:/Users/E/Documents/Codex/2026-11-19/new-chat-3/ducky-shared/`이며 Git에 복사하지 않는다.

| 스냅샷 | SHA256 |
| --- | --- |
| `Ducky_11-24_최종_구축_계획.md` | `3d43872e155496d8ad7daa4bd5616e2cc86deafe0bd2e693d0fea512870762da` |
| `AWS_PIPELINE.md` | `9736d26846d8971167283586072184089fd4edae48292672d9578d41abb9957f` |

## 11. 협업·Git 이력 정리

- 역할별 독립 worktree에서 구현한 뒤 팀장 통합 브랜치로 확정 커밋을 가져왔다. 같은 원본 checkout을 여러 워커가 바꾸지 않았다.
- 기존 독립 세션의 Run/Task/Dispatch 연결만으로 native 하위 관계라고 부른 초기 보고는 정정했다.
- 이후 팀장 `/root`가 native `frontend_final`, `harness_final`, `documents_final`, `infra_final`을 생성해 기존 각 worktree를 읽기 검토했다. 완료 뒤 가짜 대기 에이전트를 만들지 않았다.
- 실제 사이드바 들여쓰기 표시는 상위 ChatGPT의 정상 Computer Use 관측을 사용자에게 전달받은 근거다. worktree 부모 관계와 에이전트 부모 관계를 구분한다. 완료 결과는 기존 부모 대화의 하위 에이전트 시작/결과 카드에 남는다.
- 이번 작업의 신규 영어 커밋 메시지는 한글로 정리했다. 승인된 정확한 old SHA lease를 사용했고 각 tree·작성자·날짜를 보존했다. 기존 담당자 선행 커밋은 재작성하지 않았다.
- 이후 7개 지정 브랜치만 명시 refspec으로 정상 push했다. `--all`/`--mirror`/private verify push는 하지 않았다. 새 원본 RDS 원격 브랜치에는 `840b0b4`만 올라갔다.
- 이번 인계 문서는 별도 한글 커밋으로 정상 push한다. PR 본문은 기존 상위 수정본을 유지한다. 병합 시 branch 삭제·force push·원본 checkout 변경은 하지 않는다.

## 12. 재개 체크리스트

- [ ] 새 PC에서 두 저장소를 올바른 base로 clone하고 PR 병합 상태·최신 CI를 확인한다.
- [ ] 기존 PC의 원본 미커밋 20파일과 private verify·평가 입력이 Git clone에 없음을 이해하고 소유자 인계 전까지 보존한다.
- [ ] 로컬 18080/18091/13000과 격리 DB로 합성 문서 기본 동작을 확인한다. 선행 없는 코드에서 PDF 쪽 생성/BM25를 완료로 보고하지 않는다.
- [ ] 학교 콘솔 SQS/CloudFormation/S3 실제 목록과 소유권을 확인한다.
- [ ] Lambda 공식 SHA·image digest·platform·revision·ARN·계약 버전을 확보한다.
- [ ] 인프라 연결값·state/bucket/notification 소유권·backend 전용 IAM/SSM/IMDS를 확보한다.
- [ ] BM25/쪽 생성 선행의 공식 통합 범위를 별도 확정한다. private verify를 제품 PR에 섞지 않는다.
- [ ] 두 SQL의 테스트/백업/복원과 backend/Lambda 릴리스 절차를 검토한다. 운영 변경은 별도 승인 전 수행하지 않는다.
- [ ] 승인된 실제 테스트 환경에서 문서 처리·대화·재시도 복구·출처 복원·타인 격리를 확인한다.
- [ ] 고정20은 동일 hash로 비교하고 실제 LLM/실제 AWS/로컬 에뮬레이션 결과를 각각 보고한다.
- [ ] RDS 전환·라즈베리파이 연결은 기존 담당자의 별도 인계로 진행한다.

관련 저장소 문서: [로컬 처리기](../Ducky_rag/README.md), [cloud backend](../Ducky_was/docs/DOCUMENT_CLOUD.md), [대화 하네스](../Ducky_was/docs/CONVERSATION_HARNESS.md), [인프라 소유권/적용 전 검토](https://github.com/2026-YU-Capstone-Design/Ducky_infra/blob/feat/operations-monitoring/docs/document-pipeline.md).

AWS 공식 참고: [Lambda 컨테이너 이미지](https://docs.aws.amazon.com/lambda/latest/dg/images-create.html), [ECR image push](https://docs.aws.amazon.com/AmazonECR/latest/userguide/docker-push-ecr-image.html), [S3 콘솔 알림](https://docs.aws.amazon.com/AmazonS3/latest/userguide/enable-event-notifications.html), [SQS-Lambda 설정](https://docs.aws.amazon.com/lambda/latest/dg/services-sqs-configure.html).
