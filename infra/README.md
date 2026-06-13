# Seat Rush 로컬 인프라

로컬 개발에 필요한 Redis, Kafka, Kafka UI를 실행합니다.

## 구성 서비스

| 서비스 | 로컬 접속 주소 | 컨테이너 내부 주소 |
| --- | --- | --- |
| Queue Redis | `localhost:6379` | `queue-redis:6379` |
| Seat Redis | `localhost:6380` | `seat-redis:6379` |
| Kafka | `localhost:9092` | `kafka:29092` |
| Kafka UI | `http://localhost:8089` | `kafka-ui:8080` |

## 실행

```powershell
docker compose -f infra/docker-compose.local.yml up -d
```

## 상태 확인

```powershell
docker compose -f infra/docker-compose.local.yml ps
```

## 종료

```powershell
docker compose -f infra/docker-compose.local.yml down
```

볼륨까지 삭제하려면 다음 명령을 사용합니다.

```powershell
docker compose -f infra/docker-compose.local.yml down -v
```

Queue Service는 `QUEUE_REDIS_HOST`, `QUEUE_REDIS_PORT`를 사용하고 Ticket Service는 `SEAT_REDIS_HOST`, `SEAT_REDIS_PORT`를 사용합니다.
