# Seat Rush 1차 클라우드 배포

## 구성

1차 배포는 비용을 줄이기 위해 애플리케이션과 메시징 인프라를 하나의 EC2에서 컨테이너로 실행한다.

```text
Internet
  |
  | 80 / 443
  v
Nginx + Certbot
  |-- /api/* -> API Gateway
  `-- /*      -> Frontend

API Gateway
  |-- Ticket Service ------> MySQL / seat_rush_ticket
  |-- Queue Service -------> Queue Redis
  `-- Payment Service -----> MySQL / seat_rush_payment

Ticket Service -----------> Seat Redis
Notification Consumer ---> Notification Redis
Ticket / Queue / Payment / Notification <-> Kafka
```

- 외부 공개 포트는 `80`, `443`만 사용한다.
- Gateway와 각 서비스, Redis, Kafka는 Docker 내부 네트워크에서만 접근한다.
- 서버와 인프라 Compose는 `seat-rush-production-network`를 공유한다.
- MySQL은 Docker 내부 네트워크에만 연결하고 외부에 `3306`을 공개하지 않는다.
- Nginx가 외부 요청을 분배하고 Certbot이 Let's Encrypt 인증서를 발급·갱신한다.

운영 Compose는 변경 주기에 따라 분리한다.

| 파일 | 구성 | 일반적인 변경 주기 |
| --- | --- | --- |
| `docker-compose.infra.yml` | MySQL, Kafka, Redis | 낮음 |
| `docker-compose.server.yml` | 프론트, Gateway, Spring 서비스, Nginx, Certbot | 높음 |

## 권장 사양

- EC2: Amazon Linux 2023 ARM, `t4g.large` 권장
- Storage: gp3 30GB 이상

MySQL, Kafka와 Spring Boot 서비스 5개를 동시에 실행하므로 4GB 메모리에서는 여유가 작다. 비용 때문에 `t4g.medium`을 사용한다면 Swap 설정과 실제 메모리 사용량 확인이 필요하다.

## 네트워크

### EC2 보안 그룹

| 포트 | 소스 | 용도 |
| --- | --- | --- |
| `22` | 관리자 IP | SSH |
| `80` | `0.0.0.0/0`, `::/0` | HTTP 및 인증서 발급 |
| `443` | `0.0.0.0/0`, `::/0` | HTTPS |

`8080`부터 `8084`, Redis, Kafka 포트는 외부에 개방하지 않는다.

## EC2 준비

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

재접속한 뒤 Docker Compose를 확인한다.

```bash
docker compose version
```

저장소를 내려받고 배포 파일을 준비한다.

```bash
git clone <repository-url> seat-rush
cd seat-rush
cp infra/.env.production.example infra/.env.production
chmod +x infra/scripts/*.sh
```

## MySQL 구성

MySQL은 `docker-compose.infra.yml`에서 실행한다. 첫 실행 시 초기화 스크립트가 데이터베이스와 서비스별 사용자를 자동 생성한다.

Flyway는 애플리케이션 시작 시 각 서비스가 소유한 데이터베이스에 마이그레이션을 적용한다.

- Ticket Service: `seat_rush_ticket`
- Payment Service: `seat_rush_payment`
- Ticket과 Payment는 서로 다른 DB 사용자를 사용한다.
- MySQL 포트는 EC2 외부에 공개하지 않는다.

초기화 스크립트는 MySQL 볼륨이 비어 있는 첫 실행에만 동작한다. DB 이름이나 계정을 변경하려면 기존 데이터를 백업한 뒤 직접 변경하거나 새 볼륨으로 초기화해야 한다.

## 인증 키

키 파일은 Git에 커밋하지 않고 EC2의 `infra/secrets`에만 저장한다.

```bash
mkdir -p infra/secrets

openssl genpkey -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -out infra/secrets/access-token-private-key.pem
openssl rsa \
  -pubout \
  -in infra/secrets/access-token-private-key.pem \
  -out infra/secrets/access-token-public-key.pem

openssl genpkey -algorithm RSA \
  -pkeyopt rsa_keygen_bits:2048 \
  -out infra/secrets/entry-token-private-key.pem
openssl rsa \
  -pubout \
  -in infra/secrets/entry-token-private-key.pem \
  -out infra/secrets/entry-token-public-key.pem

chmod 600 infra/secrets/*-private-key.pem
chmod 644 infra/secrets/*-public-key.pem
```

필요한 파일은 다음과 같다.

```text
infra/secrets/
  access-token-private-key.pem
  access-token-public-key.pem
  entry-token-private-key.pem
  entry-token-public-key.pem
```

## 환경변수

`infra/.env.production`에서 다음 값을 설정한다.

- `DOMAIN`
- `CERTBOT_EMAIL`
- `MYSQL_ROOT_PASSWORD`
- Ticket DB 이름, 사용자, 비밀번호
- Payment DB 이름, 사용자, 비밀번호
- 토큰 TTL과 대기열 입장 허용 인원

`.env.production`과 PEM 키는 `.gitignore` 대상이다.

## 도메인과 HTTPS

1. EC2에 Elastic IP를 연결한다.
2. Route 53 또는 사용 중인 DNS에서 도메인의 `A` 레코드를 Elastic IP로 지정한다.
3. EC2 보안 그룹의 `80`, `443` 포트를 연다.
4. `DOMAIN`과 `CERTBOT_EMAIL`을 입력하고 Compose를 실행한다.

최초 배포 시 `initialize-https.sh`가 다음 순서로 인증서를 구성한다.

1. 임시 자체 서명 인증서 생성
2. Nginx 실행
3. Certbot webroot 방식으로 Let's Encrypt 인증서 발급
4. 실제 인증서로 교체한 뒤 Nginx reload

Certbot은 12시간마다 갱신 여부를 확인하고 Nginx는 6시간마다 인증서를 다시 읽는다.

## 배포

최초 배포에서는 인프라와 서버를 순서대로 실행한다.

```bash
./infra/scripts/deploy.sh
```

이후 애플리케이션 코드만 변경됐다면 서버 Compose만 다시 빌드한다.

```bash
./infra/scripts/deploy-server.sh
```

MySQL, Kafka 또는 Redis 구성을 변경했을 때만 인프라를 갱신한다.

```bash
./infra/scripts/deploy-infra.sh
```

상태와 로그를 확인한다.

```bash
docker compose \
  -f infra/docker-compose.infra.yml \
  ps

docker compose \
  --env-file infra/.env.production \
  -f infra/docker-compose.server.yml \
  ps

docker compose \
  --env-file infra/.env.production \
  -f infra/docker-compose.server.yml \
  logs -f --tail=200
```

## 재실행

설정 변경 없이 컨테이너를 다시 올린다.

```bash
./infra/scripts/restart.sh
```

코드가 변경됐다면 다시 빌드하는 `deploy-server.sh`를 실행한다. 이 명령은 Kafka와 Redis를 재생성하지 않는다.

## E2E 확인

```bash
./infra/scripts/verify.sh https://seat-rush.example.com
```

추가로 다음 흐름을 브라우저에서 확인한다.

```text
회원가입 -> 로그인 -> 공연/회차 선택 -> 대기열
-> 좌석 선점 -> 예매 생성 -> Mock 결제 -> 예매 확정
```

## GitHub Actions 배포

최초 배포와 EC2 설정은 수동으로 수행한다. 이후 `main` 브랜치에 머지되면 `.github/workflows/deploy.yml`이 서버 Compose를 다시 빌드하고 배포한다.

GitHub 저장소의 `Settings -> Environments`에서 `production` 환경을 생성한다. 필요하면 Required reviewers를 설정해 승인 후 배포되도록 구성한다.

`Settings -> Secrets and variables -> Actions`에 다음 값을 등록한다.

### Secret

| 이름 | 값 |
| --- | --- |
| `EC2_SSH_PRIVATE_KEY` | EC2 접속용 개인 키 전체 내용 |

### Variable

| 이름 | 예시 |
| --- | --- |
| `EC2_HOST` | EC2 Elastic IP 또는 도메인 |
| `EC2_USER` | `ec2-user` |
| `EC2_APP_DIR` | `/home/ec2-user/seat-rush` |

기본 동작은 다음과 같다.

```text
main push
-> EC2 SSH 접속
-> git pull --ff-only origin main
-> deploy-server.sh
-> HTTPS E2E 확인
```

GitHub Actions의 `Run workflow`에서 배포 대상을 선택할 수 있다.

- `server`: 애플리케이션 컨테이너만 다시 빌드
- `all`: MySQL, Kafka, Redis를 포함한 전체 배포

MySQL, Kafka, Redis는 일반 코드 배포에서 재시작하지 않으므로 기본값은 `server`이다.

## 장애 확인

```bash
docker compose \
  --env-file infra/.env.production \
  -f infra/docker-compose.server.yml \
  logs ticket-service queue-service payment-service
```

MySQL, Redis와 Kafka 데이터는 인프라 Compose의 Docker named volume에 저장된다. 서버 Compose를 재배포하거나 종료해도 영향을 받지 않는다. 인프라 Compose에 `docker compose down -v`를 실행하면 운영 데이터까지 삭제되므로 사용하지 않는다.

EC2 인스턴스를 삭제하기 전에는 MySQL을 백업한다.

```bash
./infra/scripts/backup-mysql.sh
```

생성된 `infra/backups/mysql-*.sql.gz` 파일은 로컬 PC나 S3 등 EC2 외부에 보관한다.

## 후속 개선

- EC2 단일 장애 지점 제거
- MySQL을 RDS로 이전
- Redis를 ElastiCache로 이전
- Kafka를 MSK 또는 별도 클러스터로 이전
- ALB와 ECS 적용
- CloudWatch 로그 및 메트릭 수집
- AWS Secrets Manager 또는 SSM Parameter Store 적용

## 참고

- [EC2 Elastic IP](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/elastic-ip-addresses-eip.html)
- [Route 53에서 EC2로 트래픽 연결](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-to-ec2-instance.html)
