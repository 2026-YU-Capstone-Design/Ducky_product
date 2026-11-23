# Ducky_product

## 로컬에서 수정하고 테스트하기

문서 업로드·대화까지 확인하려면 프론트, Spring 백엔드, Python 문서 처리기가 필요합니다.
Java 17, Node.js 24 이상, Python 3.12를 설치한 뒤 **저장소 루트**에서 실행하세요.
Windows PowerShell에서는 `npm` 대신 `npm.cmd`를 사용해도 됩니다.

```sh
# 처음 한 번 / 의존성 변경 시
npm run setup:local

# 매번 개발 시작 시: 세 서비스가 함께 실행됩니다.
npm run dev
```

준비 완료 안내 뒤 **http://127.0.0.1:13000**을 열고 로컬 계정을 만드세요.
첫 설치와 최초 Java 실행은 패키지·Gradle 다운로드 때문에 몇 분 걸릴 수 있습니다. 설치 완료 후 개발 명령을 실행하세요.
API는 18080, 문서 처리기는 18091입니다. API 주소와 CORS를 자동으로 맞춥니다.
기존에 사용 중인 포트가 있으면 실행을 중단하며 다른 프로세스를 종료하지 않습니다.
`Ctrl+C`는 이번 명령이 시작한 서비스만 함께 종료합니다.
Windows에서 일괄 작업 종료 `(Y/N)` 확인이 나오면 `Y`를 입력하세요.

```sh
# 기본 포트가 이미 사용 중인 경우
npm run dev -- --api-port 18082 --worker-port 18093 --web-port 13002
```

- AWS·Docker·별도 PostgreSQL·LLM 키 없이 사용할 수 있습니다. 답변은 로컬 발췌 모드이며 실제 LLM 품질 테스트와 구분합니다.
- DB와 업로드 파일은 Git 제외 경로 `.runtime/local-dev/<API포트>/`에 보관하며 종료 뒤에도 유지합니다. 운영 DB 설정 대신 로컬 H2를 사용합니다.
- 프론트 수정은 Next 개발 서버가 반영합니다. Java 수정은 IDE에서 컴파일하거나 `Ctrl+C` 후 다시 `npm run dev`로 빌드·시작하세요. Python 수정 후에도 다시 시작하세요.
- 프론트 화면만 작업하려면 기존대로 `Ducky_web`에서 `npm run dev`를 사용할 수 있습니다. 이 명령만으로 backend API가 실행되지는 않습니다.
- 초기 설치/Gradle 의존성 다운로드에는 인터넷이 필요합니다. 운영 AWS/LLM 서비스에는 연결하지 않습니다.

검증: `npm run test:local`은 포트 충돌, 준비 시간 초과, 자식 서비스 실패 및 종료 시 다른 프로세스 보존을 검사합니다.
전체 작업 결과·별도 수동 실행·AWS 준비는 [다른 로컬 인계 문서](docs/DUCKY_1123_HANDOFF.md)를 참고하세요.
