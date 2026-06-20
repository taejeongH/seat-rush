# 좌석 조회 및 대기열 경로 성능 개선

## 목적

1차 부하 테스트에서 확인된 좌석 조회 지연과 대기열 polling 오류의 원인을 구간별로 확인하고,
검증된 병목부터 개선합니다.

기준 측정 결과는 [100명·500명·1,000명 예매 흐름 단계별 측정 결과](../load-test-results/2026-06-20-k6-100-500-1000.md)에서 확인합니다.

## 개선 후보

아래 항목은 1차 측정 결과와 코드 구조를 바탕으로 정리한 가설입니다. 원인으로 확정하지 않고,
계측과 재측정으로 검증합니다.

| 우선순위 | 대상 | 관찰된 현상 또는 구조 | 개선 가설 | 검증 방법 |
| ---: | --- | --- | --- | --- |
| 1 | 좌석 조회 | 100명 p95 2.18s, 1,000명 p95 3.32s | 구역 2,500석 DB 조회, 2,500개 hold 키 MGET, DTO/JSON 생성·전송 중 병목 구간 미확정 | DB 조회·Redis MGET·응답 크기·직렬화 시간을 각각 측정 |
| 2 | 좌석 hold 상태 | 전체 좌석 ID로 MGET 후 Boolean Map 생성 | 구역별 활성 hold ID만 조회하면 선점 수가 적을 때 Redis 전송량을 줄일 수 있음 | 현재 MGET과 section ZSet 기반 held seat 조회의 명령 수·payload·p95 비교 |
| 3 | 좌석 응답 형식 | 매 요청마다 구역 좌석 전체를 응답 | 정적 배치도 캐시와 동적 hold 상태를 분리하거나 viewport/page 조회로 응답 크기를 줄일 수 있음 | 응답 바이트, 프론트 렌더링 시간, 재조회 빈도 비교 |
| 4 | Lua Script | hold·연장·해제가 Redis 단일 스레드에서 원자 실행 | 현재 최대 4석이라 Script 자체는 짧을 가능성이 높고, 제거보다 실행 시간 계측이 우선 | Redis command latency, script 실행 시간, CPU, slowlog 확인 |
| 5 | 대기열 polling | 1,000명에서 `queues/me` EOF/연결 종료 발생 | Gateway 연결/HTTP 커넥션, Queue Service 처리량, polling 집중이 원인 후보 | Gateway 5xx, 연결 수, Queue Redis 지연, Queue API p95/p99를 같은 시각에 비교 |
| 6 | 예매 생성 | 500명부터 p95 2.54s | hold 검증·TTL 연장 Redis 작업과 DB 예매 저장의 누적 비용 또는 커넥션 경합 가능성 | Redis/DB 구간별 타이머, HikariCP active/pending, DB 슬로우 쿼리 확인 |

## 적용한 개선

| 개선 항목 | 개선 전 구조 | 적용한 변경 | 기대 효과 | 개선 후 확인할 값 |
| --- | --- | --- | --- | --- |
| 실제 좌석 조회 | 회차 존재 확인, 구역 소속 확인, 좌석 조회를 각각 DB에 요청 | 토큰 회차 검증은 유지하고 `sectionId + scheduleId` 조건의 좌석 조회 한 번으로 통합 | 좌석 조회당 DB 왕복 2회 감소 | `seat.query.repository` p90/p95/p99, 전체 좌석 조회 p90/p95/p99 |
| 연습 좌석 조회 | 레이아웃 존재 확인, 구역 소속 확인, 좌석 조회를 각각 DB에 요청 | 토큰·세션 검증 후 `sectionId + layoutId` 조건의 좌석 조회 한 번으로 통합 | k6 연습 시나리오의 좌석 조회당 DB 왕복 2회 감소 | `seat.query.repository{mode="practice"}` p90/p95/p99, 전체 좌석 조회 p90/p95/p99 |
| 좌석 조회 계측 | HTTP 응답 시간만 확인 가능 | Repository, Redis hold 조회, DTO 변환, 전체 서비스 시간을 `seat.query.*`로 분리 기록 | 병목을 JPA·Redis·애플리케이션 구간으로 구분 | `seat.query.repository`, `seat.query.hold.read`, `seat.query.mapping`, `seat.query` 비중 |
| 연습 세션 정리 | `KEYS practice:{sessionId}:*` 후 일괄 삭제 | `SCAN`으로 세션 키를 순차 조회하고 500개 단위로 삭제 | Redis 전체 키 공간 블로킹 방지 | 세션 종료 시간, Redis command latency, 삭제 대상 키 수 |

## 재측정 조건

- 연습 좌석 배치 ID `1`, 전체 10,000석, 구역당 2,500석
- 사용자당 1~4석 선택, 최대 5회 선점 재시도
- 결제 성공 100%, 중도 이탈 0%
- 본 측정 전 10명 워밍업 후 30초 대기
- 사용자 수: 100명 → 500명 → 1,000명
- 사용자 수별 최소 3회 실행 후 p90/p95/p99 중앙값 기록

## 재측정 결과

| 동시 사용자 | 개선 전 좌석 조회 p95 | 개선 후 좌석 조회 p95 | 변화율 | 개선 후 Repository p95 | 개선 후 `seat.query.hold.read` p95 | 개선 후 `seat.query.mapping` p95 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 2,180 ms | 3,237 ms | +1,057 ms (+48.5%) | 645 ms | 441 ms | 7 ms |
| 500 | 2,822 ms | 측정 예정 | 측정 예정 | 측정 예정 | 측정 예정 | 측정 예정 |
| 1,000 | 3,324 ms | 측정 예정 | 측정 예정 | 측정 예정 | 측정 예정 | 측정 예정 |

`seat.query.*`는 이번 개선에서 처음 추가했으므로, 구간별 메트릭은 개선 전 수치가 없습니다.
표의 Repository 값은 메트릭 이름 변경 전 `seat.query.static`으로 기록한 동일 구간의 과거 결과입니다.
개선 효과는 우선 기존 HTTP 좌석 조회 p95와 비교하고, 이번 재측정부터는 구간별 비중도 함께
기록해 다음 개선의 근거로 사용합니다.

### 1차 재측정 결과

100명 조건을 동일한 워밍업·안정화 조건으로 3회 반복한 결과, 좌석 조회 p95 중앙값은 `3,237ms`로 개선 전 `2,180ms`보다
높았습니다. 기능 실패는 없었지만, 개선 효과를 확인하지 못했으므로 500명·1,000명 확대 측정은
보류합니다. 상세 결과는 [개선 후 100명 재측정 결과](../load-test-results/2026-06-20-k6-after-seat-query-100-users/README.md)를 참고합니다.

새 메트릭에서는 `seat.query` p95 중앙값이 `838ms`, Repository 구간이 `645ms`, Redis hold 조회가
`441ms`, DTO 변환이 `7ms`였습니다. 외부 k6 좌석 조회 p95와의 차이가 크므로 다음 개선은 DB·Redis
자료구조 변경보다 JSON 직렬화·응답 전송·Gateway 대기 구간을 계측하는 방향으로 진행합니다.

## 설계 원칙

- 좌석 선점 Lua Script는 다중 좌석을 전부 성공시키거나 전부 실패시키는 원자성에 필요하므로,
  성능 우려만으로 제거하지 않습니다.
- entryToken의 서명·사용자·회차 claim 검증은 좌석 선택 단계의 접근 제어이므로 유지합니다.
  줄일 대상은 권한 검증이 아니라 중복 DB 조회와 대량 응답 처리입니다.
- section ZSet 같은 새 Redis 인덱스를 도입한다면, hold 생성·연장·해제와 TTL 만료 후 정리가
  기존 좌석별 hold 키와 항상 일치하도록 Lua Script에서 함께 갱신해야 합니다.

## 다음 재측정 기반

MySQL `EXPLAIN ANALYZE`에서 연습 좌석 2,500건 조회는 `idx_seat_layout_seats_section_sort` 인덱스를
사용했고 실제 실행 시간은 약 3.7ms였습니다. 따라서 `seat.query.static`이라는 기존 이름은 순수 DB
시간으로 오해될 수 있어 `seat.query.repository`로 변경합니다.

다음 재측정에서는 아래 구간을 같은 k6 시간 창에서 비교합니다.

| 비교 구간 | 확인하려는 원인 |
| --- | --- |
| k6 좌석 조회 p95 ↔ Gateway p95 | 외부 네트워크·Nginx와 Gateway 프록시 처리 비중 |
| Gateway p95 ↔ Ticket MVC p95 | Gateway에서 Ticket Service까지의 프록시·응답 전달 비중 |
| Ticket MVC p95 ↔ `seat.query` p95 | Controller, 인터셉터, Jackson 직렬화, 응답 캐시 처리 비중 |
| `seat.query.repository` ↔ MySQL 실행 계획 | JDBC 결과 수신, Hibernate 엔티티 생성·영속성 컨텍스트 처리 비중 |
| HikariCP active/pending/max | DB 커넥션 풀 포화 또는 대기 여부 |
| JVM GC pause | 대량 엔티티·JSON 객체 생성으로 인한 GC 영향 여부 |

측정 결과는 `collect-seat-query-metrics.ps1`로 k6 요약 파일과 같은 디렉터리에 저장합니다.
