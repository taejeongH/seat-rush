# Seat Rush 인프라

## 로컬 실행

```powershell
docker compose -f infra/docker-compose.local.yml up -d
```

| 서비스 | 호스트 주소 |
| --- | --- |
| Queue Redis | `localhost:6379` |
| Seat Redis | `localhost:6380` |
| Notification Redis | `localhost:6381` |
| Kafka | `localhost:9092` |
| Kafka UI | `http://localhost:8089` |

## 운영 배포

운영 구성과 AWS 설정은 [배포 문서](../docs/deployment.md)를 참고한다.

```bash
cp infra/.env.production.example infra/.env.production
sh infra/scripts/deploy.sh
```

운영 Compose는 변경 주기에 따라 분리되어 있다.

| 파일 | 구성 |
| --- | --- |
| `docker-compose.infra.yml` | MySQL, Kafka, Redis |
| `docker-compose.server.yml` | 프론트엔드, Gateway, Spring 서비스, Nginx, Certbot |

일반적인 애플리케이션 배포에서는 인프라를 재시작하지 않고 서버만 갱신한다.

```bash
sh infra/scripts/deploy-server.sh
```

EC2를 삭제하기 전 MySQL 데이터를 백업한다.

```bash
sh infra/scripts/backup-mysql.sh
```
