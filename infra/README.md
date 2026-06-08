# Seat Rush 로컬 인프라

로컬 개발에 필요한 Redis, Kafka, Kafka UI를 실행합니다.

## 구성 서비스

| 서비스 | 로컬 접속 주소 | 컨테이너 내부 접속 주소 |
| --- | --- | --- |
| Redis | `localhost:6379` | `redis:6379` |
| Kafka | `localhost:9092` | `kafka:29092` |
| Kafka UI | `http://localhost:8089` | `kafka-ui:8080` |

## 실행 명령어

```powershell
docker compose -f infra/docker-compose.local.yml up -d
```

## 상태 확인

```powershell
docker compose -f infra/docker-compose.local.yml ps
```

## 로그 확인

```powershell
docker compose -f infra/docker-compose.local.yml logs -f
```

## 종료

```powershell
docker compose -f infra/docker-compose.local.yml down
```

## 볼륨까지 삭제

```powershell
docker compose -f infra/docker-compose.local.yml down -v
```

## 로컬 애플리케이션 설정

Spring 서비스를 개발 PC에서 직접 실행할 때 사용합니다.

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.kafka.bootstrap-servers=localhost:9092
```
