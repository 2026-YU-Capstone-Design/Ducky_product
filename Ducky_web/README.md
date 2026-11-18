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

`main`에 병합하면 `publish.yml`이 이미지를 GHCR에 올리고, AWS Systems Manager(SSM)를 통해 EC2에서 프론트엔드 컨테이너를 교체한다. GitHub 러너가 `ducklab.site`의 커밋 SHA와 첫 화면의 빌드 ID를 확인한다. 확인에 실패하면 Actions에 실패로 표시한다. Actions의 수동 실행은 이미지 발행까지만 진행한다.

### 최초 설정

- 운영 EC2가 SSM에서 **Online**이고 root의 Docker가 프론트엔드 이미지를 pull할 수 있어야 한다.
- GitHub Actions Variables에 `FRONTEND_INSTANCE_ID`, `FRONTEND_AWS_REGION`, `FRONTEND_DEPLOY_ROLE_ARN`을 등록한다. OIDC 역할은 이 저장소의 `main`을 신뢰하고, `ssm:SendCommand`를 운영 인스턴스와 `AWS-RunShellScript` 문서로 제한한다. 실행 결과를 읽을 `ssm:GetCommandInvocation` 권한도 필요하다. [GitHub OIDC 설정](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)

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

최초 전환 중 이름을 바꾼 뒤 오류가 나면, 같은 터미널에서 아래 명령으로 원본을 복구한다. 이후 정상 배포는 Actions에서 처리한다.

```bash
compose rm --stop --force frontend && sudo docker rename ducky-frontend-before-compose ducky-frontend && sudo docker start ducky-frontend
```

### 배포 후 수동 복구

컨테이너 교체 직전의 이미지는 EC2에 `ducky-frontend-rollback:previous`로 남는다. 교체 후 복구가 필요할 때 EC2의 `/opt/ducky/frontend`에서 실행한다.

```bash
sudo env FRONTEND_IMAGE=ducky-frontend-rollback:previous docker compose -p ducky-frontend up -d --no-deps --pull never frontend
```
