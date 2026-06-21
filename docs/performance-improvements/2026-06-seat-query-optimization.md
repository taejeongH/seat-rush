# 좌석 조회 성능 분석 및 개선

## 목적

티켓 오픈 상황을 재현한 부하 테스트에서 좌석 조회 응답 시간이 길게 나타났습니다.
측정으로 병목을 확인하고, 확인된 원인을 순서대로 개선합니다.

## 진행 순서

1. 전체 예매 흐름 부하 테스트로 좌석 조회 지연을 확인했습니다.
2. 개선 1에서 중복 DB 검증 조회를 제거했습니다.
3. 개선 2에서 HTTP 응답 버퍼링을 제거했습니다.
4. 개선 3에서 JPA constructor projection을 적용했습니다.
5. 이후 병목 분석 기반을 강화해 HikariCP와 Redis hold 조회를 세분화해 계측했습니다.

## 1차 부하 테스트 결과

환경은 EC2 `t4g.large`, Docker Compose, 로컬 PC의 k6입니다. 연습 배치의 전체 10,000석 중
사용자가 선택한 구역의 2,500석을 조회합니다.

| 동시 사용자 | 좌석 조회 p95 | 전체 HTTP p95 | 예매 확정 | 특이 사항 |
| ---: | ---: | ---: | ---: | --- |
| 100명 | 2,180 ms | 1,527 ms | 100 / 100 | 정상 완료 |
| 500명 | 2,822 ms | 1,486 ms | 500 / 500 | 정상 완료 |
| 1,000명 | 3,324 ms | 2,670 ms | 956 / 1,000 | `queues/me` 연결 종료 일부 발생 |

상세 결과: [100명·500명·1,000명 단계별 측정 결과](../load-test-results/2026-06-20-k6-100-500-1000.md)

좌석 조회는 단순 SQL 한 번이 아니라 다음 작업을 포함합니다.

```text
구역의 좌석 2,500개 조회
→ 좌석 ID 2,500개로 Redis hold 상태 조회
→ hold 상태와 좌석 정보 조합
→ DTO 생성 및 JSON 직렬화
→ Ticket Service와 API Gateway를 거친 HTTP 응답 전송
```

## 개선 1: 중복 DB 검증 조회 제거

### 문제

기존 좌석 조회는 좌석 목록을 조회하기 전에 회차 또는 배치 존재 여부와 구역 소속을 별도로 조회한 뒤,
다시 좌석 목록을 조회했습니다.

### 적용한 변경

- 실제 예매 좌석 조회는 `sectionId + scheduleId` 조건의 단일 조회로 회차·구역 소속 검증과 좌석 목록 조회를 함께 처리합니다.
- 연습 모드 좌석 조회는 `sectionId + layoutId` 조건의 단일 조회로 배치·구역 소속 검증과 좌석 목록 조회를 함께 처리합니다.

### 측정 결과와 해석

초기 100명 기준선의 좌석 조회 p95는 `2,180ms`였고, 이 변경 후 첫 재측정에서는 `3,237ms`가 나왔습니다.
기능 오류는 없었지만 워밍업과 서버 상태 차이가 큰 단일 측정이어서 효과를 확정할 수 없었습니다.
이후 개선부터는 사전 부하, 3회 반복, 중앙값 기록 방식으로 측정 절차를 통일했습니다.

상세 결과: [좌석 조회 개선 후 100명 사용자 결과](../load-test-results/2026-06-20-k6-after-seat-query-100-users/README.md)

## 개선 2: HTTP 응답 버퍼링 제거

### 문제와 적용한 변경

`RequestCachingFilter`는 모든 응답을 `ContentCachingResponseWrapper`로 감싼 뒤,
응답 본문 전체를 메모리에 보관하고 마지막에 복사했습니다. 응답 본문을 로그로 사용하지 않아,
응답 버퍼링은 제거하고 요청 본문 로그를 위한 request wrapper만 유지했습니다.

### 재측정 결과

| 지표 | 실행 1 | 실행 2 | 실행 3 | 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| 좌석 조회 p95 | 2,380 ms | 1,647 ms | 2,151 ms | 2,151 ms |
| Ticket Servlet p95 | 1,683 ms | 1,636 ms | 2,127 ms | 1,683 ms |
| API Gateway p95 | 2,320 ms | 1,648 ms | 2,144 ms | 2,144 ms |
| 전체 HTTP p95 | 1,811 ms | 1,347 ms | 1,700 ms | 1,700 ms |

첫 실행의 5초대 이상치는 사라졌지만 실행별 변동이 남아, 버퍼링 제거만의 효과로 단정하지 않았습니다.

상세 결과: [응답 버퍼링 제거 후 좌석 조회 사용자 100명](../load-test-results/2026-06-21-k6-response-buffer-removed-100-users/README.md)

## 개선 3: JPA constructor projection 적용

### 문제

좌석 목록 응답에는 `seatId`, `rowName`, `seatNumber`, 상태만 필요하지만, 기존 구현은 2,500개의
`Seat` 또는 `SeatLayoutSeat` 엔티티를 생성하고 영속성 컨텍스트에 등록했습니다.

### 적용한 변경

- 실제 예매 좌석 조회는 `SeatQueryProjection`으로 필요한 열만 JPQL constructor projection으로 조회합니다.
- 연습 모드 좌석 조회는 `SeatLayoutSeatQueryProjection`으로 필요한 열만 조회합니다.
- DTO 조합 시 요청 경로의 `sectionId`를 사용해 좌석 엔티티의 `section` 연관 관계를 참조하지 않습니다.

### 재측정 결과

| 지표 | 응답 버퍼링 제거 후 중앙값 | Projection 적용 후 중앙값 | 변화 |
| --- | ---: | ---: | ---: |
| 좌석 조회 p95 | 2,151 ms | 1,652 ms | -499 ms (-23.2%) |
| `seat.query.repository` p95 | 332 ms | 111 ms | -221 ms (-66.6%) |
| `seat.query` p95 | 550 ms | 415 ms | -135 ms (-24.5%) |
| Ticket Servlet p95 | 1,683 ms | 1,517 ms | -166 ms (-9.9%) |
| API Gateway p95 | 2,144 ms | 1,668 ms | -476 ms (-22.2%) |

Repository 처리 시간은 크게 줄었지만, Redis hold 조회와 HikariCP 대기 가능성이 다음 분석 대상으로 남았습니다.

상세 결과: [JPA Projection 적용 후 좌석 조회 사용자 100명](../load-test-results/2026-06-21-k6-projection-100-users/README.md)

## 병목 분석 기반 강화: HikariCP 및 Redis hold 세부 계측

### 문제

기존 지표는 `seat.query.repository`, `seat.query.hold.read`처럼 넓은 구간만 보여 줬습니다.
DB 커넥션 획득 대기, 2,500개 hold key 생성, Redis `MGET`, 결과 매핑 중 어디가 병목인지 구분할 수 없었습니다.

또한 순간 부하를 5초 주기로 수집하면 일부 지표를 놓칠 수 있어, Spring 애플리케이션 Prometheus scrape 주기를 1초로 조정했습니다.

### 적용한 계측

- hold 조회를 키 생성, Redis `MGET`, 결과 매핑 구간으로 분리 계측했습니다.
- HikariCP의 active, pending, acquire, usage, timeout 지표를 수집했습니다.
- Lettuce `MGET` 호출 시간과 Seat Redis 서버 `MGET` 처리 시간을 함께 수집했습니다.
- 100명 사전 부하 1회와 60초 안정화 후, 실행 간 30초 수집 창을 분리했습니다.

### 측정 결과

사전 부하와 60초 안정화 후 정상 완료한 3회 결과의 중앙값은 다음과 같습니다.

| 지표 | 중앙값 | 해석 |
| --- | ---: | --- |
| 좌석 조회 p95 | 2,016 ms | 본 측정 3회 중앙값 |
| Repository p95 | 126 ms | projection 적용 후 DB 결과 처리 부담은 낮음 |
| hold 조회 전체 p95 | 356 ms | 좌석 조회 내부의 주요 구간 |
| hold key 2,500개 생성 p95 | 77 ms | 대량 key 문자열 생성 비용 발생 |
| Redis `MGET` 호출 p95 | 289 ms | Ticket Service 클라이언트 호출 경로 지연 |
| Seat Redis `MGET` 평균 | 0.38 ms | Redis 서버 명령 처리 자체는 빠름 |
| Lettuce `MGET` 평균 | 65 ms | 클라이언트 측 대기와 통신 비용 존재 |
| HikariCP active 최대 | 10 | 풀 최대치 사용 |
| HikariCP pending 최대 | 75 | 전체 예매 흐름에서 커넥션 획득 대기 발생 |
| HikariCP acquire 평균 | 460 ms | DB 커넥션 획득 대기 비중 확인 |
| HikariCP timeout | 0 | timeout 전 단계의 대기 상태 |

### 해석

- Seat Redis 서버가 아닌 Ticket Service의 hold 조회 경로가 우선 개선 대상입니다.
- 구역별 선점 좌석 인덱스를 도입하면 2,500개 hold key 생성과 전체 `MGET` 범위를 줄일 수 있습니다.
- HikariCP 수치는 예매 생성·결제 결과 처리 등 전체 흐름의 DB 작업을 포함하므로, 좌석 조회 Repository만의 문제라고 단정하지 않습니다.
  다음 개선에서는 DB 사용 경로별 커넥션 사용량을 분리 계측합니다.

상세 결과: [HikariCP 및 Redis hold 세부 계측 사용자 100명](../load-test-results/2026-06-21-k6-hikari-redis-metrics-100-users/README.md)

## 다음 개선 후보

다음 실제 코드 변경은 `개선 4`로 기록합니다.

1. 구역별 선점 좌석 인덱스를 도입해 2,500개 hold key 생성과 `MGET` 조회 범위를 줄입니다.
2. 예매 생성·결제 결과 처리 등 DB 사용 경로별 커넥션 사용량을 분리 계측합니다.
3. 개선 후 같은 조건으로 100명을 재측정하고, 결과가 안정적이면 500명과 1,000명으로 확장합니다.
