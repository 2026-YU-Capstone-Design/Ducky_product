## 👥 팀원 소개


| <img src="https://avatars.githubusercontent.com/u/84740013?v=4" width="150" height="150"/> | <img src="https://avatars.githubusercontent.com/u/139312570?v=4" width="150" height="150"/> |
|:-------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------:|
|                                             FE                                              |                                             FE                                              |                                             
|                  Kim Jeong Un<br/>[@vni911](https://github.com/vni911)                   |                      Shin Hye Jin<br/>[@hyeji-neee](https://github.com/hyeji-neee)                      |

<br>

### **Commit Message 규칙**

| 메시지 타입 | 설명 |
| :--- | :--- |
| **feat** | 새로운 기능을 추가하거나 기존 기능을 수정할 때 사용합니다. |
| **fix** | 버그를 수정할 때 사용합니다. |
| **docs** | 문서를 수정할 때 사용합니다. |
| **style** | 코드의 스타일이나 포맷을 변경할 때 사용합니다(기능 변경 없음). |
| **revert** | 이전 커밋으로 되돌릴 때 사용합니다. |

## 프론트엔드 배포

`main`에 병합하면 `publish.yml`이 프론트엔드·백엔드 이미지를 GHCR에 올린다. 두 이미지 발행이 성공하면 프론트엔드의 `deploy` 태그를 갱신한다. EC2는 2분마다 이 태그를 확인하고, 새 이미지가 있으면 digest를 고정해 Compose 컨테이너를 교체한다. 기존 다운로드가 진행 중이면 다음 확인까지 더 걸릴 수 있다.

GitHub 러너는 최대 10분 동안 `ducklab.site`의 커밋 SHA와 첫 화면의 빌드 ID를 확인한다. 이미지 발행만으로 배포 성공을 표시하지 않는다. 실패 시 자동 복구하지 않으며 아래 수동 복구 절차를 따른다. Actions의 수동 실행은 이미지 발행까지만 진행하고 `deploy` 태그를 바꾸지 않는다.

이 방식은 AWS OIDC·SSM·AWS 액세스 키를 사용하지 않는다. 기존 OIDC 역할은 공급자 미등록으로 인증·배포를 검증하지 못한 상태이며, 무료 플랜의 고급 기능을 활성화할 필요가 없다. 기존 AWS Actions Variables는 이 워크플로에서 사용하지 않는다.

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
