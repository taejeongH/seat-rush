# Seat Rush 모니터링 가이드

## 목적

배포 환경에서 장애 원인과 성능 병목을 빠르게 확인하기 위한 모니터링 기준을 정리합니다.

## 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| Prometheus | 메트릭 수집과 alert rule 평가 |
| Grafana | 대시보드 시각화 |
| Alertmanager | Prometheus alert 수신 |
| node-exporter | EC2 CPU, 메모리, 디스크, 네트워크 |
| cAdvisor | Docker 컨테이너 리소스 |
| redis_exporter | Queue/Seat/Notification Redis 상태 |
| mysqld_exporter | MySQL 상태 |
| kafka-exporter | Kafka broker, topic, consumer lag |

## 주요 지표

| 영역 | 지표 |
| --- | --- |
| HTTP | RPS, p90/p95/p99 latency, 5xx rate |
| Business | 대기열 진입, 순번 조회, 입장 토큰 발급, 좌석 선점, 예매 생성, 결제 결과 반영 |
| JVM | heap usage, GC, thread |
| DB | HikariCP active/max connection |
| Redis | connected clients, memory, command rate |
| Kafka | consumer lag, broker availability |
| Host | CPU, memory, disk |

## 커스텀 비즈니스 메트릭

핵심 흐름은 기본 HTTP 지표와 별도로 `seat.rush.business.*` 메트릭을 남깁니다.

| 메트릭 | 설명 |
| --- | --- |
| `seat_rush_business_duration_seconds` | 비즈니스 작업별 처리 시간 |
| `seat_rush_business_events_total` | 비즈니스 작업별 성공/실패 횟수 |

공통 태그:

| 태그 | 설명 |
| --- | --- |
| `operation` | `queue.join`, `seat.hold`, `reservation.create` 같은 작업 이름 |
| `mode` | `real` 또는 `practice` |
| `result` | `success` 또는 `failure` |

## 실행

```bash
sh infra/scripts/deploy-monitoring.sh
```

확인:

```bash
docker compose \
  --env-file infra/.env.production \
  -f infra/docker-compose.monitoring.yml \
  ps
```

## 접속

Prometheus, Grafana, Alertmanager는 EC2 내부 `127.0.0.1`에만 포트를 엽니다. 외부 보안그룹을 열지 말고 SSH 터널로 접속합니다.

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

## 부하 테스트 전 기준 지표

부하 테스트를 시작하기 전에 다음 값을 기록합니다.

```md
## 기준 지표

- 테스트 일시:
- EC2 사양:
- 실행 중인 컨테이너:
- API Gateway p90/p95/p99:
- Ticket Service p90/p95/p99:
- Queue Service p90/p95/p99:
- Payment Service p90/p95/p99:
- Business operation p95:
- JVM heap max usage:
- HikariCP active/max:
- Redis memory:
- Kafka consumer lag:
- Host CPU:
- Host memory:
```

## 알림 기준

| 알림 | 기준 |
| --- | --- |
| 서비스 다운 | Prometheus scrape 실패 2분 이상 |
| HTTP 오류 증가 | 5xx 응답 초당 0.1개 초과 |
| p95 지연 증가 | HTTP p95 1초 초과 5분 이상 |
| p99 지연 증가 | HTTP p99 2초 초과 5분 이상 |
| 비즈니스 실패 증가 | 핵심 작업 실패 초당 0.1개 초과 |
| JVM 메모리 압박 | heap 85% 초과 5분 이상 |
| DB 커넥션 풀 포화 | active/max 85% 초과 3분 이상 |
| 호스트 리소스 압박 | CPU/메모리 80~85% 이상 |
| Redis 장애 | `redis_up = 0` |
| Kafka 장애 | broker 수 1 미만 |

## 개선 기록 방식

부하 테스트와 성능 개선은 다음 형식으로 남깁니다.

```md
## 성능 개선 기록

- 문제:
- 관측 지표:
- 원인:
- 수정 내용:
- 개선 전:
- 개선 후:
- 남은 리스크:
```
