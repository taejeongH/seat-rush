# Seat Rush 성능 테스트 계획

## 목적

티켓 오픈 시점에 발생하는 동시 요청을 재현하고, 대기열, 좌석 조회, 좌석 선점,
예매, 결제 결과 처리 구간의 처리량과 응답 시간을 측정합니다.

성능 테스트 결과는 `k6` 기준으로 기록합니다. 이전에 만든 가상 사용자 경쟁 기능은
사용자가 화면에서 체험하는 데모 기능으로 두고, 성능 한계 측정과 병목 분석에는
사용하지 않습니다.

## 테스트 원칙

- 부하 생성은 k6로 수행합니다.
- 같은 조건으로 반복 실행할 수 있어야 합니다.
- 각 본 측정 직전에 10명 워밍업을 실행하고 워밍업 결과는 성능 결과에서 제외합니다.
- 한 번에 모든 흐름을 섞기보다 병목 후보를 분리해서 측정합니다.
- 최종적으로 실제 예매에 가까운 복합 흐름을 측정합니다.
- 개선 전후는 동일한 조건으로 재측정합니다.

## 부하 생성 위치

현재는 비용을 줄이기 위해 로컬 PC에서 k6를 실행하고 배포 서버로 요청을 보냅니다.
이 방식은 100명, 500명 수준의 기준선 측정에는 충분하지만, 로컬 PC의 CPU, 네트워크,
백그라운드 프로세스가 부하 생성 능력에 영향을 줄 수 있습니다.

따라서 결과를 해석할 때는 k6 결과만 보지 않고 EC2 내부의 Prometheus/Grafana 지표를
함께 봅니다. 서버 CPU, 메모리, DB Connection Pool, Redis, Kafka 지표가 안정적인데
k6 쪽에서만 지연이 커진다면 부하 생성기 또는 네트워크 병목일 가능성이 있습니다.

10,000명 이상 한계 측정에서는 부하 생성기를 별도 EC2 또는 여러 대의 k6 인스턴스로
분리하는 것이 더 정확합니다. 이때도 테스트 대상 서버와 부하 생성 서버의 지표를
따로 기록해야 합니다.

## 배포 환경

| 항목 | 값 |
| --- | --- |
| 배포 방식 | 단일 EC2 + Docker Compose |
| 인스턴스 타입 | t4g.large |
| OS | Amazon Linux 2023 |
| Gateway | Spring Cloud Gateway |
| DB | MySQL 컨테이너, Ticket/Payment DB 분리 |
| Redis | Queue Redis, Seat Redis, Notification Redis 분리 |
| Kafka | 단일 broker, KRaft 모드 |
| 모니터링 | Prometheus, Grafana, Alertmanager, exporter |
| Reverse Proxy | Nginx |

## 테스트 데이터와 API 비용 조건

성능 수치는 동시 사용자 수만으로 해석하지 않습니다. 같은 API라도 좌석 수, 구역 크기,
좌석 선택 수, 활성 입장 인원처럼 요청당 처리해야 하는 데이터 양에 따라 비용이 달라집니다.
아래 값은 2026-06-20 기준 연습 예매 흐름 측정에 사용한 기본 조건입니다.

### 연습 좌석 배치

| 항목 | 값 |
| --- | --- |
| 좌석 배치 ID | `1` (`Seat Rush Arena 10000`) |
| 전체 좌석 수 | 10,000석 |
| 구역 수 | 4개 |
| 구역별 좌석 수 | Practice A/B/C/D 각각 2,500석 |
| 구역 내부 구조 | 25개 행(P1~P25) × 행당 100석 |
| 좌석 조회 범위 | 선택한 구역의 좌석 2,500석 전체 |
| 영구 좌석 상태 | MySQL의 `seat_layout_seats` 또는 `seats` |
| 임시 선점 상태 | Seat Redis의 좌석별 hold 키 |

현재 좌석 조회 API는 선택 구역의 정적 좌석 2,500건을 DB에서 읽고, 구역별 Redis hold 인덱스에서
현재 선점된 좌석 ID만 조회해 임시 선점 상태를 합칩니다. 따라서 좌석 조회의 응답 시간은 DB 조회,
구역 인덱스 조회, DTO 생성, JSON 직렬화, 네트워크 응답 크기의 영향을 함께 받습니다.

### 예매 흐름 변수

| 항목 | 현재 측정값 | API 영향 |
| --- | ---: | --- |
| 동시 사용자 | 100, 500, 1,000명 | 전체 요청 동시성, DB/Redis/HTTP 연결 경합 |
| 활성 입장 가능 인원 | 100명 | 동시에 좌석 조회·선점 단계로 진입하는 사용자 상한 |
| 사용자당 선택 좌석 수 | 1~4석 | Lua Script가 다루는 좌석 키 수와 예매 좌석 저장 건수 |
| 좌석 선점 재시도 | 최대 5회 | 인기 좌석 충돌 시 좌석 조회·선점 호출량 증가 |
| 좌석 hold TTL | 5분 | 임시 선점 키의 생명주기와 정리 시점 |
| entryToken TTL | 10분 | 좌석 선택 단계 권한의 유효 기간 |
| 대기열 polling 주기 | 1초 | `queues/me` 요청량과 Gateway/Queue Service 부하 |
| 연습 세션 데이터 TTL | Queue 30분, Ticket 결과 3시간 | Redis 메모리 점유와 세션 정리 필요성 |

> 현재 k6 시나리오는 사용자가 4개 구역 중 하나를 임의로 선택합니다. 따라서 평균적으로
> 구역당 전체 사용자의 약 25%가 분산되지만, 특정 구역 또는 특정 좌석으로 요청을 의도적으로
> 집중시키는 인기 좌석 경합 시나리오는 별도로 측정해야 합니다.

## 단계별 테스트

| 단계 | 사용자 수 | 목적 |
| --- | ---: | --- |
| 1 | 100 | 기준선 측정, 전체 흐름 정상 여부 확인 |
| 2 | 500 | 초기 병목 후보 확인 |
| 3 | 1,000 | 단일 EC2 기준 안정성 확인 |
| 4 | 3,000 | 대기열, Redis, Gateway 부하 확인 |
| 5 | 5,000 | 서버 한계 근접 구간 확인 |
| 6 | 10,000 | 대규모 티켓 오픈 상황 검증 |
| 7 | 100,000 | 최종 한계 측정 목표, 실패 양상과 병목 지점 기록 |

### 워밍업 절차

JVM JIT, DB Connection Pool, Redis·MySQL 캐시가 차가운 상태의 영향을 줄이기 위해 각 본 측정은
아래 순서로 실행합니다.

1. 동일 시나리오로 10명 워밍업 실행
2. Kafka lag와 진행 중인 요청이 기준 상태로 돌아올 때까지 대기
3. 본 측정 실행: 100명, 500명, 1,000명 등
4. 다음 사용자 수 측정 전 1번부터 반복

워밍업은 실제 티켓 오픈 대기 시간이 아닌 5초 카운트다운으로 실행해, 필요한 API 경로만 빠르게
데웁니다. 워밍업 결과와 본 측정 결과는 서로 다른 연습 세션으로 분리됩니다.

### 단계 진행 및 개선 판단 방식

`1,000명`은 개선을 시작해야 하는 고정 기준이 아니라, 낮은 부하부터 지연과 자원 사용량이
어떤 곡선으로 증가하는지 확인하는 첫 판단 지점입니다. 특정 단계에서 병목이 명확해지면
더 높은 부하를 그대로 반복하기보다 원인을 분석하고 개선 전후를 같은 조건으로 비교합니다.

| 구간 | 진행 방식 | 다음 행동 |
| --- | --- | --- |
| 100 → 1,000명 | 정상 흐름과 증가 추세 확인 | 모든 핵심 지표가 안정적이면 3,000명으로 진행 |
| 3,000 → 10,000명 | 단일 EC2에서의 한계 구간 확인 | 병목이 명확하면 개선 후 같은 단계 재측정 |
| 100,000명 | 최종 한계 탐색 | 단일 로컬 k6가 아닌 분산 부하 생성 환경에서 진행 |

예를 들어 1,000명에서 DB Connection Pool 포화, Kafka lag의 지속 증가, p99 급등처럼
원인이 명확한 문제가 보이면 3,000명 이상을 계속 올려도 같은 실패 양상만 반복될 가능성이
큽니다. 이 경우 해당 병목을 개선하고 100·500·1,000명을 다시 측정해 효과를 확인한 뒤
다음 단계로 진행합니다. 반대로 지표가 안정적이면 개선 없이 3,000·5,000·10,000명까지
연속 측정해 한계가 나타나는 지점을 찾습니다.

## 시나리오 구성

성능 테스트는 시나리오별로 나누는 것이 맞습니다. 실제 서비스에서는 여러 요청이 섞이지만,
처음부터 모든 흐름을 한 번에 섞으면 어떤 구간이 병목인지 찾기 어렵습니다.

| 시나리오 | 목적 | 주요 지표 |
| --- | --- | --- |
| 대기열 진입 | 오픈 시점 동시 진입 처리량 측정 | RPS, p95/p99, Redis 처리 |
| 순번 조회 | 대기 중 Polling 부하 측정 | RPS, Gateway CPU, Queue Service latency |
| 입장 토큰 발급 | 입장 가능 사용자 토큰 발급 성능 측정 | p95/p99, Redis Lua 실행 영향 |
| 좌석 조회 | 많은 좌석 데이터를 조회할 때 응답 시간 측정 | p95/p99, 응답 크기, Ticket Service CPU |
| 좌석 선점 | 인기 좌석 동시 선점 경합 측정 | 성공률, 실패율, p95, Redis 처리 |
| 예매 생성 | hold 기반 예매 생성과 DB 트랜잭션 성능 측정 | p95, HikariCP active ratio |
| 결제 완료 | 결제 결과 이벤트 발행과 소비 흐름 측정 | p95, Kafka consumer lag |
| 전체 예매 흐름 | 실제 사용자 흐름에 가까운 복합 부하 측정 | 성공률, 실패율, 전체 소요 시간 |

기준선 테스트에서는 결제 실패와 중간 이탈을 섞지 않습니다. 정상 예매 흐름 100%를 먼저
측정해야 시스템 자체의 기본 처리 성능을 비교하기 쉽습니다. 결제 실패, 중도 이탈,
재시도 같은 사용자 행동은 별도 시나리오에서 측정합니다.

## 기록 지표

| 분류 | 지표 |
| --- | --- |
| 사용자 관점 | 성공률, 실패율, 전체 흐름 소요 시간 |
| HTTP | RPS, 평균 응답 시간, p90, p95, p99 |
| 비즈니스 구간 | 대기열 진입, 입장 토큰 발급, 좌석 조회, 좌석 선점, 예매 생성, 결제 완료 p95 |
| 좌석 경합 | 선점 재시도 충돌 수, 최종 선점 실패 수 |
| DB | HikariCP active ratio, DB 관련 오류 |
| Kafka | Consumer lag, 이벤트 처리 지연 |
| 서버 | CPU, Memory, Disk, Network |
| 부하 생성기 | k6 CPU 사용률, 네트워크 사용량, dropped iterations 여부 |

### 좌석 조회 구간별 메트릭

좌석 조회 개선 후에는 전체 HTTP 시간만 보지 않고, Ticket Service가 노출하는
`seat_rush_business_duration_seconds` 메트릭을 함께 확인합니다. 모든 메트릭은
`mode=real` 또는 `mode=practice` 태그로 실제 예매와 연습 모드를 구분합니다.

| operation 태그 | 측정 범위 |
| --- | --- |
| `seat.query` | 토큰 검증부터 좌석 목록 응답 DTO 생성까지의 전체 서비스 처리 시간 |
| `seat.query.repository` | Spring Data JPA Repository 호출 전체 시간. JDBC 결과 수신과 Hibernate 엔티티 생성도 포함되므로 순수 MySQL 실행 시간과 구분해서 해석 |
| `seat.query.hold.read` | 구역별 Redis hold 인덱스를 읽고 좌석 상태로 변환하는 전체 시간 |
| `seat.query.hold.redis.index` | Sorted Set 인덱스에서 현재 선점 좌석 ID를 조회하는 시간 |
| `seat.query.mapping` | DB 좌석과 hold 상태를 응답 DTO로 합치는 시간 |

### 좌석 조회 응답 경로 메트릭

좌석 목록은 2,500석처럼 큰 JSON 응답을 만들 수 있으므로, 서비스 메서드 외부의 응답 처리 시간도
함께 기록합니다. 아래 메트릭은 `GET /api/.../seats` 요청에만 기록됩니다.

| 메트릭 | 측정 범위 |
| --- | --- |
| `seat_rush_response_duration_seconds{stage="servlet"}` | Ticket Service Filter부터 Controller·Interceptor·Service·Jackson JSON 직렬화·응답 쓰기가 끝날 때까지의 시간 |
| `seat_rush_gateway_duration_seconds` | API Gateway가 좌석 목록 요청을 Ticket Service로 프록시하고 응답을 완료할 때까지의 시간 |

`seat.query.repository`와 MySQL `EXPLAIN ANALYZE`의 결과는 다르게 해석합니다. 전자는 JPA와
애플리케이션의 객체 처리까지 포함하고, 후자는 MySQL 엔진 내부의 SQL 실행 계획과 실행 시간을 나타냅니다.

### 측정 결과 수집

k6 실행이 끝난 직후, 같은 티켓 오픈 시간 창의 Prometheus 지표를 아래 스크립트로 저장합니다.
Prometheus는 SSH 터널을 연 상태에서 기본 주소 `http://127.0.0.1:9090`으로 접근합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\load-test\k6\scripts\collect-seat-query-metrics.ps1 `
  -SummaryPath .\docs\load-test-results\<result-directory>\summary.json `
  -OutputPath .\docs\load-test-results\<result-directory>\seat-query-metrics.json
```

스크립트는 `seat.query.*`, Ticket Service 응답 처리, Gateway 처리, HikariCP active/pending/max,
JVM GC pause를 같은 60초 창에서 수집합니다.

Spring 애플리케이션의 Prometheus scrape 간격은 성능 테스트 중 짧은 커넥션 풀 피크를 놓치지 않도록
5초로 설정합니다.

테스트가 끝난 직후에는 현재 시점을 기준으로 조회합니다. 이미 끝난 실행을 다시 수집할 때는
오픈 시각 이후의 종료 시점을 지정할 수 있습니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\load-test\k6\scripts\collect-seat-query-metrics.ps1 `
  -SummaryPath .\docs\load-test-results\<result-directory>\summary.json `
  -OutputPath .\docs\load-test-results\<result-directory>\seat-query-metrics.json `
  -EndOffsetSeconds 45
```

Prometheus에서 연습 모드 좌석 조회 p95를 확인하는 예시는 아래와 같습니다.

```promql
histogram_quantile(
  0.95,
  sum by (le, operation) (
    rate(seat_rush_business_duration_seconds_bucket{
      mode="practice",
      operation=~"seat\\.query.*"
    }[1m])
  )
)
```

## 성능 판단 기준

아래 기준은 현재 단일 `t4g.large` 배포 환경에서 개선 우선순위를 정하기 위한 기준입니다.
최종 SLA가 아니라, 테스트 결과를 일관되게 해석하기 위한 내부 기준입니다.

| 지표 | 안정적 | 관찰 필요 | 개선 필요 |
| --- | ---: | ---: | ---: |
| HTTP 실패율 | 0.1% 미만 | 0.1% 이상 1% 미만 | 1% 이상 |
| 최종 좌석 선점 실패율 | 1% 미만 | 1% 이상 3% 미만 | 3% 이상 |
| 핵심 API p95 | 1s 미만 | 1s 이상 1.5s 미만 | 1.5s 이상 |
| 핵심 API p99 | 2s 미만 | 2s 이상 3s 미만 | 3s 이상 |
| HikariCP active ratio | 70% 미만 | 70% 이상 85% 미만 | 85% 이상 |
| Kafka consumer lag | 빠르게 0으로 회복 | 일시 증가 후 회복 | 지속 증가 |
| 서버 CPU | 70% 미만 | 70% 이상 85% 미만 | 85% 이상 지속 |
| 메모리 | 지속 증가 없음 | 완만한 증가 | OOM, swap, 지속 증가 |

좌석 조회처럼 응답 데이터가 큰 API는 p95 1.5s까지 관찰 대상으로 둘 수 있지만,
대기열 진입, 입장 토큰 발급, 좌석 선점처럼 사용자가 즉시 체감하는 API는 p95 1s 미만을
우선 목표로 둡니다.

좌석 선점 재시도 중 발생하는 400/409 응답은 인기 좌석 경합에서 자연스럽게 발생하는
정상 결과입니다. 따라서 HTTP 실패율에는 포함하지 않고, `seat_hold_retry_conflict`로
분리해서 기록합니다. 성능 실패로 보는 값은 재시도를 모두 사용해도 좌석을 선점하지 못한
`seat_hold_final_failure`입니다.

## 결과 문서 기준

성능 테스트 결과 문서는 다음 항목을 중심으로 작성합니다.

- 테스트 목적
- 테스트 조건
- 부하 생성 위치와 한계
- 사용자 행동 분포
- 전체 결과
- 구간별 응답 시간
- 주요 해석
- 개선 후보
- 주요 Grafana 지표 이미지
- k6 원본 결과

이미 구현한 내용을 모르는 사람이 봐도 이해하기 어려운 지표는 본문에서 제외하거나,
왜 보는 지표인지 설명할 수 있을 때만 포함합니다.

## 측정 결과 관리

이전 시범 측정값은 기준 결과로 사용하지 않습니다. EC2 실행 환경을 준비한 뒤 아래 순서로
새 기준선을 만들고, 각 단계의 결과를 `docs/load-test-results/<날짜>-k6-<사용자수>-users`에
기록합니다.

1. 100명
2. 500명
3. 1,000명
4. 3,000명
5. 5,000명
6. 10,000명
7. 100,000명

첫 단계 결과는 [100명·500명·1,000명 예매 흐름 단계별 측정 결과](./load-test-results/2026-06-20-k6-100-500-1000.md)에서 확인합니다.
