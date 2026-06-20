# 좌석 조회 응답 경로 기준선: 100명

## 실행 조건

| 항목 | 값 |
| --- | --- |
| 대상 환경 | EC2 `t4g.large`, Docker Compose |
| 부하 생성기 | 로컬 PC의 k6 |
| 대상 주소 | 배포 API Gateway |
| 좌석 배치 | 연습 배치 ID `1`, 전체 10,000석, 구역당 2,500석 |
| 사용자 행동 | 결제 성공 100%, 중도 이탈 0% |
| 워밍업 | 각 본 측정 전 10명 전체 흐름 실행 후 30초 대기 |
| 반복 횟수 | 100명 본 측정 3회 |
| 응답 버퍼링 | `ContentCachingResponseWrapper` 적용 상태 |
| Prometheus Spring App scrape | 15초, 다음 재측정부터 5초 적용 |

## k6 결과

| 항목 | 실행 1 | 실행 2 | 실행 3 | 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| 예매 확정 | 100 / 100 | 100 / 100 | 100 / 100 | 100 / 100 |
| HTTP 실패율 | 0.000% | 0.000% | 0.000% | 0.000% |
| 전체 HTTP p95 | 2,822 ms | 2,967 ms | 2,034 ms | 2,822 ms |
| 좌석 조회 p90 | 3,282 ms | 3,859 ms | 2,443 ms | 3,282 ms |
| 좌석 조회 p95 | 3,594 ms | 4,018 ms | 2,633 ms | 3,594 ms |
| 좌석 조회 p99 | 3,795 ms | 4,653 ms | 3,325 ms | 3,795 ms |
| HTTP RPS | 38.26 | 44.02 | 50.62 | 44.02 |

## 응답 경로 p95

| 구간 | 실행 1 | 실행 2 | 실행 3 | 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| `seat.query` | 1,017 ms | 775 ms | 614 ms | 775 ms |
| `seat.query.repository` | 704 ms | 513 ms | 435 ms | 513 ms |
| `seat.query.hold.read` | 381 ms | 447 ms | 338 ms | 381 ms |
| `seat.query.mapping` | 5 ms | 7 ms | 3 ms | 5 ms |
| Ticket MVC | 1,719 ms | 1,564 ms | 2,498 ms | 1,719 ms |
| 응답 캐시 복사 | 1,195 ms | 1,545 ms | 96 ms | 1,195 ms |
| API Gateway | 3,101 ms | 3,289 ms | 2,856 ms | 3,101 ms |

## 자원 지표

| 지표 | 실행 1 | 실행 2 | 실행 3 | 해석 |
| --- | ---: | ---: | ---: | --- |
| HikariCP active 최대 | 10 | 0 | 0 | 15초 scrape 간격이 짧은 피크를 놓칠 수 있음 |
| HikariCP pending 최대 | 75 | 0 | 0 | 실행 1에서 커넥션 풀 대기 관찰 |
| HikariCP max | 10 | 10 | 10 | Ticket Service 커넥션 풀 상한 |
| JVM GC pause 합계 | 775 ms | 976 ms | 747 ms | 대량 객체 생성 영향 후보 |

## 분석

- MySQL `EXPLAIN ANALYZE`에서는 `idx_seat_layout_seats_section_sort` 인덱스를 사용했고,
  좌석 2,500건 조회가 약 3.7ms로 끝났습니다. DB 엔진의 SQL 실행 자체가 주 병목은 아닙니다.
- `seat.query.repository`는 JPA Repository 호출 전체를 포함하므로 JDBC 결과 수신과 Hibernate 엔티티
  생성 비용은 남아 있습니다. 다만 좌석 조회 전체 p95보다 응답 처리와 Gateway 구간이 더 큽니다.
- `ContentCachingResponseWrapper` 복사 p95가 최대 1.54초까지 나타났습니다. 응답 본문을 로그에
  사용하지 않는 구조이므로, 다음 변경에서 버퍼링을 제거하고 실제 Servlet 응답 시간을 재측정합니다.
- HikariCP gauge는 15초 간격으로 수집되어 짧은 실행의 순간 피크를 일관되게 담지 못했습니다.
  다음 측정부터 Spring 애플리케이션 scrape 간격을 5초로 적용합니다.

## 원본 결과

- [실행 1](./runs/run-1/summary.json), [Prometheus 스냅샷](./runs/run-1/seat-query-metrics.json)
- [실행 2](./runs/run-2/summary.json), [Prometheus 스냅샷](./runs/run-2/seat-query-metrics.json)
- [실행 3](./runs/run-3/summary.json), [Prometheus 스냅샷](./runs/run-3/seat-query-metrics.json)

모든 `summary.json`에서는 access token을 제거했습니다.
