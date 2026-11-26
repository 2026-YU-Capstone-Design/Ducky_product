# Ducky Web

**Next.js 16 · React 19 · TypeScript · Tailwind CSS 4**

Ducky의 웹 클라이언트입니다. 사용자는 텍스트나 음성으로 질문하고, 디버깅 힌트와 문서 출처를 확인합니다. 설정에서 학습 자료와 등록 기기를 관리합니다.

[제품 소개](../README.md) · [백엔드 API](../Ducky_was/README.md) · [문서 처리](../Ducky_rag/README.md)

<p align="center">
  <img src="public/images/splash1.png" alt="Ducky 시작 화면에 사용하는 캐릭터 일러스트" width="300" />
</p>

## 주요 화면

| 경로 | 화면 | 할 수 있는 일 |
| --- | --- | --- |
| `/`, `/onboarding` | 로그인·온보딩 | 이메일 로그인과 회원가입, 카카오·네이버 로그인, 학습 유형 설정 |
| `/dashboard`, `/chat` | 대시보드·채팅 | 새 대화 시작, 문서 첨부, 힌트 확인, 브라우저 음성 입력과 Raspberry Pi 녹음 요청 |
| `/sessions`, `/analysis` | 세션·분석 | 이전 대화 열기, 학습 분석 화면 조회 |
| `/settings` | 설정 | 학습 유형, 자료실, 연동 기기 관리 |

## 개발 시작

채팅·자료 업로드까지 확인하려면 [저장소 루트의 통합 실행](../README.md#빠른-시작)을 사용하세요. 웹만 실행하려면 `Ducky_web`에서 Node.js 24 이상으로 아래 명령을 실행합니다.

```sh
npm ci
npm run dev
```

개발 서버 기본 주소는 [localhost:3000](http://localhost:3000)입니다. 로그인과 자료실을 사용하려면 별도로 접근 가능한 Spring API가 필요합니다. 필요한 값을 `.env.local`에 설정하고 개발 서버를 재시작하세요.

| 환경 변수 | 용도 |
| --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | Spring API의 기본 주소. 미설정 시 `http://localhost:8080` |
| `NEXT_PUBLIC_KAKAO_REST_API_KEY` | 카카오 OAuth 클라이언트 설정 |
| `NEXT_PUBLIC_NAVER_CLIENT_ID` | 네이버 OAuth 클라이언트 설정 |

다른 포트의 웹에서 Spring을 호출할 때는 백엔드 CORS 허용 목록에 웹 주소를 추가합니다. OAuth 클라이언트 비밀값은 [Spring 환경 변수](../Ducky_was/.env.example)로 관리합니다.

## 코드와 검증

| 디렉터리 | 역할 |
| --- | --- |
| [src/app](src/app) | App Router 페이지 |
| [src/components](src/components) | 인증, 채팅, 설정 등 화면 구성 요소 |
| [src/hooks](src/hooks) | 대화 세션, 음성 입력, 인증 상태 |
| [src/lib/api](src/lib/api) | Spring API 요청과 기기 명령 상태 처리 |

```sh
npm run lint
npm test
npm run build
```

## 배포

[이미지 발행 워크플로](../.github/workflows/publish.yml)는 `main` 푸시에서 웹과 백엔드 이미지를 GHCR에 발행합니다. 두 이미지의 발행이 성공하면 웹의 `deploy` 태그가 갱신됩니다. EC2의 systemd 타이머가 2분마다 태그를 확인해 새 digest로 컨테이너를 교체합니다. 워크플로는 최대 10분 동안 운영 사이트의 커밋 SHA와 빌드 ID를 확인합니다. Actions 수동 실행은 이미지 발행까지만 진행합니다.

<details>
<summary>EC2 초기 설정, Compose 전환 및 수동 복구</summary>

### 최초 설정

- Ubuntu EC2에 Docker Compose, curl, flock, systemd가 필요하다. GHCR과 이미지 다운로드 서버에 HTTPS로 접근할 수 있어야 한다. 배포용 인바운드 포트는 추가하지 않는다.
- `/opt/ducky/frontend/compose.yaml`로 `ducky-frontend` 컨테이너를 관리하고 있어야 한다. 이미 Compose 전환을 완료했다면 아래 전환 절차를 반복하지 않는다.
- root의 Docker가 GHCR 이미지를 pull할 수 있어야 한다. 공개 이미지는 익명 접근이 가능하고, 비공개 이미지는 패키지 읽기 권한의 토큰으로 `sudo docker login ghcr.io`가 필요하다. 저장소 공개 여부와 패키지 공개 여부는 별개다.
- `deploy` 태그는 운영 배포 대상이다. GitHub의 `main`과 이미지 발행 권한을 보호한다. 배포 서비스는 서버의 고정된 Compose 파일만 사용하며 저장소의 코드를 내려받아 실행하지 않는다.

먼저 EC2에서 아래 명령으로 기존 root의 GHCR 접근을 확인한다. `unauthorized`이면 타이머 설치 전에 패키지를 읽을 수 있는 계정의 `read:packages` 토큰으로 `sudo docker login ghcr.io`를 완료해야 한다. 토큰을 저장소나 워크플로에 작성하지 않는다.

```bash
sudo docker manifest inspect ghcr.io/2026-yu-capstone-design/ducky-frontend:main >/dev/null
```

운영 변경 승인 후, 이 브랜치의 파일을 가져온 Ubuntu 저장소 루트에서 아래 명령을 한 번 실행한다. 기존 Compose 파일과 환경변수는 덮어쓰지 않는다. 가능하면 `main` 병합 전에 타이머를 설치한다.

```bash
sudo install -m 0644 Ducky_web/deploy/ducky-frontend-update.service /etc/systemd/system/
sudo install -m 0644 Ducky_web/deploy/ducky-frontend-update.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now ducky-frontend-update.timer
```

최초 `main` 병합 전에는 `deploy` 태그가 없어 갱신 작업이 실패할 수 있다. 이 경우 기존 컨테이너는 유지되고 다음 주기에 다시 확인한다. 타이머가 켜졌다는 것만으로 실제 배포 성공을 판단하지 않는다.

```bash
sudo systemctl list-timers ducky-frontend-update.timer
sudo journalctl -u ducky-frontend-update.service -n 60 --no-pager
```

### 기존 EC2 컨테이너를 Compose로 옮기기 (한 번)

운영 변경 승인 후 Ubuntu의 저장소 루트에서 실행한다. 기존 컨테이너가 Compose 관리 밖에 있을 때의 절차다. 현재 실행 옵션을 `Ducky_web/compose.yaml`과 대조하고, 추가 옵션은 서버의 `/opt/ducky/frontend/compose.override.yaml`에 반영한다.

```bash
sudo install -D -m 0644 Ducky_web/compose.yaml /opt/ducky/frontend/compose.yaml
cd /opt/ducky/frontend
old_image=$(sudo docker inspect -f '{{.Config.Image}}' ducky-frontend)
compose() { sudo env FRONTEND_IMAGE="$old_image" docker compose -p ducky-frontend "$@"; }
compose config --quiet && sudo docker rename ducky-frontend ducky-frontend-before-compose && compose create --pull never frontend
```

여기까지는 기존 컨테이너가 요청을 처리한다. 위 명령이 성공하면 같은 터미널에서 이미지, 환경변수, 실행 명령, 포트, 마운트가 일치할 때만 전환한다. 두 컨테이너가 포트를 교대하는 동안 연결이 잠시 끊길 수 있다.

```bash
settings='{{.Image}}
{{json .Config.Env}} {{json .Config.Entrypoint}} {{json .Config.Cmd}}
{{.Config.User}} {{.Config.WorkingDir}}
{{json .HostConfig.PortBindings}} {{.HostConfig.NetworkMode}} {{json .HostConfig.RestartPolicy}}
{{json .Mounts}}'
old_settings=$(sudo docker inspect -f "$settings" ducky-frontend-before-compose) &&
new_settings=$(sudo docker inspect -f "$settings" ducky-frontend) &&
test "$old_settings" = "$new_settings" &&
sudo docker stop ducky-frontend-before-compose && compose start frontend &&
curl -fsS --retry 8 --retry-all-errors --retry-delay 2 http://127.0.0.1:3000/ -o /dev/null
```

최초 전환 중 이름을 바꾼 뒤 오류가 나면, 같은 터미널에서 아래 명령으로 원본을 복구한다. 이후 정상 배포는 Actions와 EC2 타이머가 처리한다.

```bash
compose rm --stop --force frontend && sudo docker rename ducky-frontend-before-compose ducky-frontend && sudo docker start ducky-frontend
```

### 배포 후 수동 복구

컨테이너 교체 직전의 이미지는 EC2에 `ducky-frontend-rollback:previous`로 남는다. 교체 후 복구가 필요하면 먼저 타이머를 비활성화해 같은 이미지를 다시 배포하지 않도록 한다. 실행 중인 갱신은 잠금으로 기다린 후 복구한다. EC2에서 실행한다.

```bash
sudo systemctl disable --now ducky-frontend-update.timer
cd /opt/ducky/frontend
sudo flock -w 660 .deploy.lock env FRONTEND_IMAGE=ducky-frontend-rollback:previous docker compose -p ducky-frontend up -d --no-deps --pull never frontend
curl -fsS http://127.0.0.1:3000/ -o /dev/null
```

수정한 커밋이 `main`에 병합되어 새 `deploy` 이미지가 발행된 후 `sudo systemctl enable --now ducky-frontend-update.timer`로 자동배포를 재개한다. Actions가 시간 초과되더라도 타이머는 계속 확인하므로, 배포를 중단하려면 위 비활성화 명령을 실행해야 한다. 오래된 이미지 정리는 자동으로 수행하지 않는다.

</details>

## 프론트엔드 팀

| 이름 | GitHub |
| --- | --- |
| 정운 | [@vni911](https://github.com/vni911) |
| Shin Hye Jin | [@hyeji-neee](https://github.com/hyeji-neee) |

커밋 접두사는 기능 `feat`, 수정 `fix`, 문서 `docs`, 코드 스타일 `style`, 되돌리기 `revert`를 사용합니다.
