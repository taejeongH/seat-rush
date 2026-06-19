# Seat Rush 운영 배포 문서

## 배포 구조

1차 운영 환경은 비용을 줄이기 위해 하나의 EC2에서 여러 컨테이너를 실행합니다.

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

Prometheus -> Spring Actuator, exporter
Grafana    -> Prometheus
Alertmanager <- Prometheus alert rules
```

외부 공개 포트는 `80`, `443`만 사용합니다. Grafana와 Prometheus는 EC2 내부 `127.0.0.1`에만 바인딩하고 SSH 터널로 접근합니다.

## 권장 인스턴스

비용을 줄이면서 전체 컨테이너를 동시에 실행하려면 ARM 기반 인스턴스를 우선 고려합니다.

| 목적 | 권장 |
| --- | --- |
| 기능 검증 | `t4g.medium` |
| 부하 테스트와 모니터링 동시 실행 | `t4g.large` |

MySQL, Kafka, Redis 3개, Spring 서비스 5개, 모니터링 stack까지 함께 실행하면 메모리 여유가 빠르게 줄어듭니다. `t4g.medium`을 사용할 경우 부하 테스트 시 OOM 여부를 먼저 확인합니다.

## EC2 준비

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

다시 SSH 접속한 뒤 Docker Compose를 확인합니다.

```bash
docker compose version
```

저장소를 내려받습니다.

```bash
git clone <repository-url> seat-rush
cd seat-rush
cp infra/.env.production.example infra/.env.production
```

## 운영 환경변수

`infra/.env.production`에 실제 값을 입력합니다.

필수 값:

- `DOMAIN`
- `CERTBOT_EMAIL`
- `MYSQL_ROOT_PASSWORD`
- `TICKET_DB_NAME`
- `TICKET_DB_USERNAME`
- `TICKET_DB_PASSWORD`
- `PAYMENT_DB_NAME`
- `PAYMENT_DB_USERNAME`
- `PAYMENT_DB_PASSWORD`
- `GRAFANA_ADMIN_PASSWORD`

## 인증 키 생성

JWT와 entryToken 서명 키는 EC2의 `infra/secrets`에만 보관합니다.

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

chmod 644 infra/secrets/*.pem
```

## 최초 배포

인프라와 서버를 순서대로 실행합니다.

```bash
sh infra/scripts/deploy.sh
```

HTTPS 인증서를 초기화합니다.

```bash
sh infra/scripts/initialize-https.sh
```

모니터링 stack을 실행합니다.

```bash
sh infra/scripts/deploy-monitoring.sh
```

## 애플리케이션 재배포

이미 인프라가 살아 있다면 서버 컨테이너만 갱신합니다.

```bash
sh infra/scripts/deploy-server.sh
```

GitHub Actions에서는 `main` 브랜치에 머지되면 다음 순서로 동작합니다.

```text
테스트
-> ARM64 이미지 빌드
-> GHCR push
-> EC2 self-hosted runner에서 deploy-server.sh 실행
-> deploy-monitoring.sh 실행
-> HTTPS 검증
```

## 모니터링

### 수집 대상

| 대상 | 수집 방식 |
| --- | --- |
| Spring 서비스 | `/actuator/prometheus` |
| JVM, HTTP | Micrometer |
| DB Connection Pool | HikariCP Micrometer |
| Redis | `redis_exporter` |
| Kafka | `kafka-exporter` |
| MySQL | `mysqld-exporter` |
| CPU, 메모리, 디스크, 네트워크 | `node-exporter` |
| 컨테이너 리소스 | cAdvisor |

### 접속

보안그룹을 열지 않고 SSH 터널로 접속합니다.

```bash
ssh -L 3000:127.0.0.1:3000 \
    -L 9090:127.0.0.1:9090 \
    -L 9093:127.0.0.1:9093 \
    ec2-user@<EC2_PUBLIC_IP>
```

| 도구 | URL |
| --- | --- |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |
| Alertmanager | `http://localhost:9093` |

Grafana 기본 대시보드는 `Seat Rush Overview`입니다.

### 기본 알림 규칙

Prometheus alert rule로 다음 상태를 감지합니다.

- 서비스 scrape 실패
- HTTP 5xx 비율 증가
- HTTP p95 지연 증가
- JVM heap 사용률 85% 초과
- HikariCP active connection 85% 초과
- 호스트 CPU/메모리/디스크 사용률 증가
- Redis/Kafka 장애

현재 Alertmanager receiver는 기본 placeholder입니다. 실제 Slack, Discord, Email 연동이 필요하면 `infra/monitoring/alertmanager/alertmanager.yml`의 receiver를 교체합니다.

## 로그 조회

```bash
sh infra/scripts/logs.sh
sh infra/scripts/logs.sh ticket-service
sh infra/scripts/logs.sh prometheus
```

Docker `json-file` 로그는 서비스별로 `max-size=10m`, `max-file=3` 설정을 사용합니다.

## 백업과 복구

EC2 중지 전 MySQL 데이터를 백업합니다.

```bash
sh infra/scripts/backup-mysql.sh
```

복구는 새 MySQL 컨테이너 기동 후 백업 SQL을 주입합니다.

```bash
gunzip -c infra/backups/mysql-YYYYMMDD-HHMMSS.sql.gz | docker exec -i seat-rush-infra-mysql-1 mysql -uroot -p
```

## 배포 검증

기본 HTTP 검증:

```bash
sh infra/scripts/verify.sh https://<DOMAIN>
```

전체 예매 흐름 검증은 E2E 스크립트를 사용합니다.

```powershell
$env:E2E_ADMIN_ACCESS_TOKEN="관리자-accessToken"
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1 `
  -GatewayUrl "https://<DOMAIN>"
```
