# Seat Rush Infra

## 로컬 인프라 실행

```powershell
docker compose -f infra/docker-compose.local.yml up -d
```

| 서비스 | 호스트 접속 주소 |
| --- | --- |
| Queue Redis | `localhost:6379` |
| Seat Redis | `localhost:6380` |
| Notification Redis | `localhost:6381` |
| Kafka | `localhost:9092` |
| Kafka UI | `http://localhost:8089` |

## 운영 Compose 구성

| 파일 | 역할 |
| --- | --- |
| `docker-compose.infra.yml` | MySQL, Kafka, Redis |
| `docker-compose.server.yml` | Frontend, API Gateway, Spring 서비스, Nginx, Certbot |
| `docker-compose.monitoring.yml` | Prometheus, Grafana, Alertmanager, exporter |

서버 애플리케이션만 다시 배포할 때는 인프라를 재시작하지 않고 server compose만 갱신합니다.

```bash
sh infra/scripts/deploy-server.sh
```

모니터링만 적용하거나 갱신할 때는 다음 명령을 사용합니다.

```bash
sh infra/scripts/deploy-monitoring.sh
```

## 운영 환경변수

운영 서버에서는 예시 파일을 복사한 뒤 실제 값을 채웁니다.

```bash
cp infra/.env.production.example infra/.env.production
```

주요 값은 다음과 같습니다.

| 변수 | 용도 |
| --- | --- |
| `DOMAIN` | 서비스 도메인 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 비밀번호 |
| `TICKET_DB_*` | Ticket Service DB 설정 |
| `PAYMENT_DB_*` | Payment Service DB 설정 |
| `GRAFANA_ADMIN_USER` | Grafana 관리자 계정 |
| `GRAFANA_ADMIN_PASSWORD` | Grafana 관리자 비밀번호 |
| `PROMETHEUS_RETENTION_TIME` | Prometheus 지표 보관 기간 |

`.env.production`과 `infra/secrets/*`는 Git에 올리지 않습니다.

## 모니터링 접속

Prometheus, Grafana, Alertmanager는 기본적으로 EC2 내부 `127.0.0.1`에만 포트를 엽니다. 외부 보안그룹을 열지 말고 SSH 터널로 접속합니다.

```bash
ssh -L 3000:127.0.0.1:3000 \
    -L 9090:127.0.0.1:9090 \
    -L 9093:127.0.0.1:9093 \
    ec2-user@<EC2_PUBLIC_IP>
```

| 도구 | 로컬 접속 URL |
| --- | --- |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |
| Alertmanager | `http://localhost:9093` |

## 로그 확인

전체 서버 로그:

```bash
sh infra/scripts/logs.sh
```

특정 서비스 로그:

```bash
sh infra/scripts/logs.sh ticket-service
sh infra/scripts/logs.sh prometheus
```

## 백업

EC2를 중지하거나 삭제하기 전 MySQL 데이터를 백업합니다.

```bash
sh infra/scripts/backup-mysql.sh
```

백업 파일은 `infra/backups/mysql-*.sql.gz` 형식으로 생성됩니다.
