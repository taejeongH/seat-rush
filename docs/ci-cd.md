# Seat Rush CI/CD

## 구성

```text
Pull Request
  -> GitHub-hosted Runner
  -> 백엔드 테스트 / 프론트엔드 lint·build

main 머지
  -> GitHub-hosted Runner
  -> linux/arm64 Docker 이미지 빌드
  -> GHCR 업로드
  -> production 승인
  -> EC2 Self-hosted Runner
  -> docker compose pull / up
```

- PR에서는 EC2 Runner를 사용하지 않는다.
- 배포는 `main` 커밋에 대해서만 실행한다.
- MySQL, Kafka, Redis는 자동 배포에서 재시작하지 않는다.
- EC2 SSH 22번 포트는 관리자 IP에만 허용한다.
- GitHub에는 EC2 SSH 개인키와 운영 환경변수를 저장하지 않는다.

## EC2 Runner 설치

GitHub 저장소에서 다음 화면으로 이동한다.

```text
Settings
-> Actions
-> Runners
-> New self-hosted runner
-> Linux
-> ARM64
```

GitHub 화면에 표시되는 다운로드와 등록 명령을 EC2에서 실행한다. 설치 경로는 애플리케이션 저장소와 분리한다.

```bash
mkdir -p /home/ec2-user/actions-runner
cd /home/ec2-user/actions-runner
```

Runner 등록 과정에서 다음 값을 사용한다.

```text
Runner name: seat-rush-production-1
Additional label: seat-rush-production
Work folder: _work
```

등록 후 서비스로 설치한다.

```bash
sudo ./svc.sh install ec2-user
sudo ./svc.sh start
sudo ./svc.sh status
```

Runner 사용자가 Docker를 실행할 수 있어야 한다.

```bash
sudo usermod -aG docker ec2-user
```

그룹 변경 후에는 EC2에 다시 로그인하고 Runner 서비스를 재시작한다.

## GitHub Environment

다음 화면에서 `production` 환경을 생성한다.

```text
Settings
-> Environments
-> New environment
-> production
```

다음 보호 규칙을 설정한다.

- Required reviewers에 본인 계정 추가
- 관리자 우회 허용 비활성화
- Deployment branches를 `main`으로 제한
- 혼자 사용하는 저장소라면 Prevent self-review는 비활성화

Environment Variable을 추가한다.

| 이름 | 값 |
| --- | --- |
| `EC2_APP_DIR` | `/home/ec2-user/seat-rush` |

`EC2_SSH_PRIVATE_KEY`, `EC2_HOST`, `EC2_USER`는 등록하지 않는다.

## main 브랜치 보호

다음 화면에서 `main` 보호 규칙을 만든다.

```text
Settings
-> Branches 또는 Rules
-> Add branch protection rule
```

권장 설정:

- Require a pull request before merging
- Require status checks to pass before merging
- Require branches to be up to date before merging
- Block force pushes
- Block deletions

필수 상태 검사에는 백엔드 서비스 테스트와 프론트엔드 테스트를 지정한다.

## Actions 권한

다음 화면에서 워크플로 권한을 제한한다.

```text
Settings
-> Actions
-> General
```

권장 설정:

- Actions permissions는 GitHub와 Docker 공식 Action만 허용
- Workflow permissions 기본값은 Read repository contents
- Fork pull request workflow는 외부 기여자 승인을 요구

현재 워크플로는 `actions/*`, `docker/*` Action을 사용한다.

## 운영 파일

다음 파일은 Runner 작업 폴더가 아니라 고정된 애플리케이션 경로에 둔다.

```text
/home/ec2-user/seat-rush/infra/.env.production
/home/ec2-user/seat-rush/infra/secrets/*.pem
```

배포 전에 EC2 저장소가 깨끗해야 한다.

```bash
cd /home/ec2-user/seat-rush
git status
```

로컬 변경이 있으면 자동 배포는 중단된다.

## 배포

PR 검증이 통과하고 `main`에 머지되면 ARM64 이미지가 GHCR에 업로드된다. `production` 배포 승인 후 EC2 Runner가 해당 커밋 SHA 이미지로 컨테이너를 교체한다.

배포 이미지는 다음 태그를 함께 가진다.

```text
커밋 SHA
latest
```

실제 배포에서는 롤백과 추적이 가능한 커밋 SHA 태그를 사용한다.

## 주의사항

- `pull_request_target` 이벤트를 추가하지 않는다.
- PR job에 `self-hosted`를 지정하지 않는다.
- 외부 입력값을 그대로 셸 명령으로 실행하지 않는다.
- Workflow 변경도 운영 코드와 동일하게 검토한다.
- Runner와 EC2를 사용하지 않을 때는 EC2 인스턴스를 중지한다.
