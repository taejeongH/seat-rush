# 좌석 조회 성능 분석 및 개선

## 목적

티켓 오픈 상황을 재현한 1차 부하 테스트에서 좌석 조회 응답 시간이 길게 나타났습니다.
측정 결과를 기준으로 병목 구간을 계측하고, 확인된 원인을 순서대로 개선합니다.

## 진행 순서

1. 전체 예매 흐름 부하 테스트로 좌석 조회 지연을 확인했습니다.
2. 좌석 조회 전에 발생하던 중복 DB 검증 조회를 제거했습니다.
3. 좌석 조회를 DB·Redis·DTO 변환·Servlet·Gateway 구간으로 나누어 계측했습니다.
4. 모든 HTTP 응답을 버퍼링하던 `ContentCachingResponseWrapper`를 제거하고 재측정했습니다.
5. JPA 엔티티 전체 로딩을 줄이기 위해 constructor projection을 구현했습니다.
6. projection 변경을 배포한 뒤 같은 조건으로 재측정해 효과를 확인했습니다.

## 1차 부하 테스트 결과

환경은 EC2 `t4g.large`, Docker Compose, 로컬 PC의 k6이며, 연습 배치의 전체 10,000석 중
사용자가 선택한 구역의 2,500석을 조회합니다.

| 동시 사용자 | 좌석 조회 p95 | 전체 HTTP p95 | 예매 확정 | 특이 사항 |
| ---: | ---: | ---: | ---: | --- |
| 100명 | 2,180 ms | 1,527 ms | 100 / 100 | 정상 완료 |
| 500명 | 2,822 ms | 1,486 ms | 500 / 500 | 정상 완료 |
| 1,000명 | 3,324 ms | 2,670 ms | 956 / 1,000 | `queues/me` 연결 종료 일부 발생 |

상세 결과: [100명·500명·1,000명 단계별 측정 결과](../load-test-results/2026-06-20-k6-100-500-1000.md)

좌석 조회는 단순 SELECT 한 번이 아니라 다음 작업을 포함합니다.

```text
구역의 좌석 2,500개 조회
→ 좌석 ID 2,500개로 Redis hold 상태 조회
→ hold 상태와 좌석 정보 조합
→ DTO 생성 및 JSON 직렬화
→ Ticket Service와 API Gateway를 거친 HTTP 응답 전송
```

## 개선 1: 중복 DB 검증 조회 제거

### 문제

기존 좌석 조회는 좌석 목록을 조회하기 전에 회차 또는 배치 존재 여부와 구역 소속을 별도 조회한 뒤,
다시 좌석 목록을 조회했습니다. 실제 응답에 필요한 것은 해당 회차 또는 배치에 속한 특정 구역의 좌석 목록이므로,
검증을 위한 조회와 목록 조회가 나뉘어 있었습니다.

### 적용한 변경

- 실제 예매 좌석 조회는 `sectionId + scheduleId` 조건의 단일 조회로 회차·구역 소속 검증과 좌석 목록 조회를 함께 처리합니다.
- 연습 모드 좌석 조회는 `sectionId + layoutId` 조건의 단일 조회로 배치·구역 소속 검증과 좌석 목록 조회를 함께 처리합니다.
- 조건에 맞는 좌석이 없으면 기존과 동일하게 예외로 처리합니다.

### 측정 결과와 해석

초기 100명 기준선의 좌석 조회 p95는 `2,180ms`였고, 이 변경 후 첫 재측정에서는 `3,237ms`가 나왔습니다.
기능 오류는 없었지만 워밍업과 서버 상태 차이가 큰 단일 재측정이어서 DB 조회 제거 효과를 확정할 수 없었습니다.
이 때문에 이후 개선부터는 사전 부하, 3회 반복, 중앙값 기록 방식으로 측정 절차를 통일했습니다.

상세 결과: [좌석 조회 개선 후 100명 사용자 결과](../load-test-results/2026-06-20-k6-after-seat-query-100-users/README.md)

## 응답 경로 계측과 분석

좌석 조회를 다음 지표로 분리했습니다.

| 지표 | 측정 구간 |
| --- | --- |
| `seat.query.repository` | JPA Repository 호출부터 결과 반환까지 |
| `seat.query.hold.read` | Redis에서 hold 상태를 읽는 시간 |
| `seat.query.mapping` | 조회 결과를 응답 DTO로 조합하는 시간 |
| `seat.query` | Ticket Service의 좌석 조회 서비스 전체 시간 |
| `seat.rush.response.duration{stage="servlet"}` | Ticket Service에서 HTTP 응답을 작성하기까지의 전체 시간 |
| `seat.rush.gateway.duration` | API Gateway가 요청을 전달하고 응답을 반환하기까지의 시간 |

MySQL `EXPLAIN ANALYZE`에서 2,500개 좌석 조회는 `idx_seat_layout_seats_section_sort` 인덱스를 사용했고,
실행 시간은 약 `3.7ms`였습니다. 따라서 긴 응답 시간은 SQL 자체보다 JPA 엔티티 처리, Redis 통신,
응답 직렬화와 Gateway 구간을 함께 봐야 한다고 판단했습니다.

## 개선 2: HTTP 응답 버퍼링 제거

기존 `RequestCachingFilter`는 모든 응답을 `ContentCachingResponseWrapper`로 감싼 뒤,
응답 본문 전체를 메모리에 보관하고 마지막에 복사했습니다. 응답 본문을 로그로 사용하지 않아
이 버퍼링은 불필요한 메모리 복사와 응답 지연을 만들 수 있었습니다.

응답 버퍼링은 제거하고, 요청 본문 로그를 위한 request wrapper만 유지했습니다.

### 재측정 결과

실제 측정 전에 100명 전체 흐름을 한 번 실행하고 60초 안정화한 뒤, 100명 측정을 3회 반복했습니다.
사전 실행은 JVM·커넥션·캐시 초기화 영향을 줄이기 위한 절차이며 결과에는 포함하지 않았습니다.

| 실행 | 좌석 조회 p95 | Ticket Servlet p95 | Gateway p95 | 전체 HTTP p95 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 2,380 ms | 1,683 ms | 2,320 ms | 1,811 ms |
| 2 | 1,647 ms | 1,636 ms | 1,648 ms | 1,347 ms |
| 3 | 2,151 ms | 2,127 ms | 2,144 ms | 1,700 ms |
| 중앙값 | 2,151 ms | 1,683 ms | 2,144 ms | 1,700 ms |

첫 실행에서만 나타나던 5초대 이상치는 사라졌지만, 3회 결과가 `1,647~2,380ms`로 여전히 변동합니다.
따라서 응답 버퍼링 제거만으로 개선 폭을 단정하지 않고, 이후 projection 적용 결과와 같은 절차로 비교합니다.

상세 결과: [응답 버퍼링 제거 후 좌석 조회 사용자 100명](../load-test-results/2026-06-21-k6-response-buffer-removed-100-users/README.md)

## 개선 3: JPA constructor projection 적용

### 측정에서 확인한 문제

좌석 목록 응답에 필요한 값은 `seatId`, `rowName`, `seatNumber`, 상태뿐입니다. 기존 구현은 2,500개의
`Seat` 또는 `SeatLayoutSeat` 엔티티를 생성하고 영속성 컨텍스트에 등록했습니다. 이 과정은 SQL 실행 시간과 별개로
엔티티 생성, 연관 관계 처리, 메모리 사용과 GC 부담을 늘릴 수 있습니다.

### 적용한 변경

- 실제 예매 좌석 조회는 `SeatQueryProjection`으로 필요한 열만 JPQL constructor projection으로 조회합니다.
- 연습 모드 좌석 조회는 `SeatLayoutSeatQueryProjection`으로 필요한 열만 조회합니다.
- DTO 조합 시 이미 요청 경로로 받은 `sectionId`를 사용해, 각 좌석 엔티티의 `section` 연관 관계를 참조하지 않습니다.

### 기대 효과와 검증 항목

| 확인 항목 | 기대 변화 |
| --- | --- |
| `seat.query.repository` p90/p95/p99 | 엔티티 생성·영속성 컨텍스트 처리 감소 |
| JVM GC pause | 대량 엔티티·객체 생성 감소 |
| 좌석 조회 p90/p95/p99 | Repository 처리 시간 감소가 전체 응답에 미치는 영향 확인 |
| Ticket Servlet·Gateway p95 | Repository 외 구간이 병목으로 남는지 확인 |

### 재측정 결과

동일한 100명 시나리오에서 사전 부하 1회와 60초 안정화 후 3회 반복 측정했습니다.

| 지표 | 응답 버퍼링 제거 후 중앙값 | Projection 적용 후 중앙값 | 변화 |
| --- | ---: | ---: | ---: |
| 좌석 조회 p95 | 2,151 ms | 1,652 ms | -499 ms (-23.2%) |
| `seat.query.repository` p95 | 332 ms | 111 ms | -221 ms (-66.6%) |
| `seat.query` p95 | 550 ms | 415 ms | -135 ms (-24.5%) |
| Ticket Servlet p95 | 1,683 ms | 1,517 ms | -166 ms (-9.9%) |
| API Gateway p95 | 2,144 ms | 1,668 ms | -476 ms (-22.2%) |

projection은 Repository 처리 시간을 크게 줄였고, 좌석 조회 전체 p95도 감소했습니다.
다만 HikariCP pending이 최대 80까지 관찰돼 DB 커넥션 대기와 Redis hold 조회가 다음 분석 대상입니다.

상세 결과: [JPA Projection 적용 후 좌석 조회 사용자 100명](../load-test-results/2026-06-21-k6-projection-100-users/README.md)

## 다음 측정 절차

1. HikariCP pending이 발생한 시점의 DB 커넥션 사용량과 대기 원인을 확인합니다.
2. Redis hold 조회 구조와 API Gateway·Ticket Servlet 구간을 추가 계측합니다.
3. 병목 개선 후 100명 시나리오를 같은 절차로 다시 측정합니다.
4. 개선 효과와 오류율 안정성이 확인되면 500명, 1,000명으로 확장합니다.

## 추가 검토 항목

| 우선순위 | 항목 | 검토 이유 |
| ---: | --- | --- |
| 1 | Redis hold 조회 구조 | 모든 좌석 ID를 기준으로 MGET 하는 비용과 구역별 hold 인덱스의 효과 비교 |
| 2 | 좌석 응답 크기 | 정적 좌석 정보와 동적 hold 상태 분리, viewport 또는 페이지 조회 검토 |
| 3 | 대기열 polling | 1,000명에서 나타난 연결 종료 원인을 Gateway·Queue Service·Redis 지표로 분석 |
| 4 | Lua Script | hold·연장·해제 Script의 명령 수, 실행 시간, Redis CPU와 slowlog 확인 |
| 5 | 예매 생성 | Redis hold 검증과 DB 트랜잭션 구간의 HikariCP active/pending 분석 |

## 다음 측정을 위한 추가 계측

| 대상 | 추가 지표 | 확인 목적 |
| --- | --- | --- |
| DB 커넥션 풀 | acquire 평균·최대, usage 평균, timeout 증가량 | Repository 지연이 SQL·엔티티 처리보다 커넥션 획득 대기에서 발생하는지 확인 |
| 좌석 hold 조회 | 키 생성, Redis `MGET`, 결과 매핑 p95 | 2,500개 좌석 ID 기준 hold 조회의 애플리케이션 내부 비용 분리 |
| Lettuce 클라이언트 | `MGET` 호출 수, 평균·최대 완료 시간 | Ticket Service에서 Seat Redis까지의 클라이언트 통신 지연 확인 |
| Seat Redis 서버 | `MGET` 호출 수와 평균 처리 시간 | Redis 서버 처리 지연과 애플리케이션 측 지연을 구분 |
