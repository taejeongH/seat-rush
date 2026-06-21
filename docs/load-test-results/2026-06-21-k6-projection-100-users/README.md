# JPA Projection 적용 후 좌석 조회 사용자 100명

## 목적

좌석 조회에서 엔티티 전체를 생성하던 방식을 JPA constructor projection으로 변경한 뒤,
동일한 예매 연습 시나리오에서 Repository 처리 시간과 전체 응답 시간 변화를 확인합니다.

## 환경과 절차

- 대상: 배포 EC2 `t4g.large`, API Gateway 경유 HTTPS 요청
- 시나리오: 연습 세션 생성부터 대기열, 좌석 조회·선점, 예매, 결제 완료까지
- 좌석 조회: 섹션당 2,500석, 전체 10,000석
- 사전 부하: 실제 측정에서 제외한 100명 전체 흐름 1회
- 안정화: 사전 부하 완료 후 60초 대기
- 본 측정: 100명 동시 사용자, 3회 반복

## 핵심 결과

| 실행 | 예매 확정 | HTTP 실패율 | 전체 HTTP p95 (ms) | 좌석 조회 p90 (ms) | 좌석 조회 p95 (ms) | 좌석 조회 p99 (ms) | 처리량 (req/s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 100 / 100 | 0.000% | 2,233 | 2,405 | 2,594 | 2,845 | 44.95 |
| 2 | 100 / 100 | 0.000% | 1,391 | 1,418 | 1,462 | 2,256 | 53.72 |
| 3 | 100 / 100 | 0.000% | 1,612 | 1,572 | 1,652 | 2,268 | 48.94 |
| 중앙값 | 100 / 100 | 0.000% | 1,612 | 1,572 | 1,652 | 2,268 | 48.94 |

## 응답 경로 p95

| 구간 | 실행 1 | 실행 2 | 실행 3 | 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| `seat.query.repository` | 128 ms | 103 ms | 111 ms | 111 ms |
| `seat.query.hold.read` | 268 ms | 368 ms | 424 ms | 368 ms |
| `seat.query.mapping` | 1 ms | 2 ms | 7 ms | 2 ms |
| `seat.query` | 356 ms | 415 ms | 490 ms | 415 ms |
| Ticket Servlet | 1,539 ms | 1,431 ms | 1,517 ms | 1,517 ms |
| API Gateway | 2,030 ms | 1,585 ms | 1,668 ms | 1,668 ms |

## 응답 버퍼링 제거 결과와 비교

| 지표 | 응답 버퍼링 제거 후 중앙값 | Projection 적용 후 중앙값 | 변화 |
| --- | ---: | ---: | ---: |
| 좌석 조회 p95 | 2,151 ms | 1,652 ms | -499 ms (-23.2%) |
| `seat.query.repository` p95 | 332 ms | 111 ms | -221 ms (-66.6%) |
| `seat.query` p95 | 550 ms | 415 ms | -135 ms (-24.5%) |
| Ticket Servlet p95 | 1,683 ms | 1,517 ms | -166 ms (-9.9%) |
| API Gateway p95 | 2,144 ms | 1,668 ms | -476 ms (-22.2%) |

## 해석

- 모든 실행에서 100명 예매가 성공했고 HTTP 실패는 없었습니다.
- Repository p95가 크게 감소해 projection이 엔티티 생성과 영속성 컨텍스트 처리 비용을 줄인 효과를 확인했습니다.
- Redis hold 조회는 중앙값 `368ms`로 Repository보다 큰 비중을 차지합니다.
- HikariCP active는 최대 10, pending은 최대 80까지 관찰됐습니다. DB 커넥션 풀이 포화되는 구간이 남아 있으므로,
  다음 단계에서는 connection pool 대기와 DB·Redis 호출 경합을 함께 분석해야 합니다.
- 실행별 변동은 남아 있어 500명·1,000명 확장 전에는 HikariCP pending 원인을 먼저 확인합니다.

## 원본 결과

- 사전 부하: [preheat](./preheat/summary.json)
- 본 측정: [run-1](./runs/run-1/summary.json), [run-2](./runs/run-2/summary.json), [run-3](./runs/run-3/summary.json)
