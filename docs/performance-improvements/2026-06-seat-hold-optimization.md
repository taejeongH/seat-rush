# 좌석 선점 성능 분석 및 개선

## 기준선

[분산 좌석 선점 100명 기준선](../load-test-results/2026-06-22-k6-seat-hold-distributed-100-users/README.md)에서 좌석 선점 API p95 중앙값은 974ms였습니다.

내부 계측에서는 좌석 DB 검증 p95가 201ms였고, HikariCP active가 최대 10에 도달하며 pending 최대 중앙값 10이 관찰됐습니다.

## 개선: 연습 좌석 검증 중복 조회 제거

기존 `validateLayoutSeats`는 좌석 목록을 조회한 뒤 각 좌석마다 `existsByIdAndLayoutId`로 배치 소속을 다시 확인했습니다.

`SeatLayoutSeatRepository.findAllByIdIn`은 EntityGraph로 `section`, `section.layout`을 함께 조회하므로, 이미 메모리에 있는 `seat.getSection().getLayout().getId()`와 요청한 좌석 배치 ID를 비교하도록 변경했습니다.

좌석 1개 요청은 DB 조회 2회에서 1회로 줄고, 최대 4개 좌석 요청에서는 좌석 수만큼 추가되던 소속 확인 조회가 제거됩니다.

## 재측정 결과

[개선 후 분산 좌석 선점 100명 재측정](../load-test-results/2026-06-23-k6-seat-hold-layout-validation-100-users/README.md)에서 좌석 선점 API p95 중앙값은 738ms로 감소했습니다.

| 지표 | 개선 전 | 개선 후 | 변화 |
| --- | ---: | ---: | ---: |
| 좌석 선점 API p95 | 974 ms | 738 ms | 24.2% 감소 |
| 좌석 DB 검증 p95 | 201 ms | 102 ms | 49.2% 감소 |
| HikariCP pending 최대 중앙값 | 10 | 0 | 커넥션 대기 해소 |

## 다음 검증

- 동일 좌석 경쟁 시나리오에서 Redis Lua 충돌 처리 비용 측정
- 사용자당 다중 좌석 선점 시 DB 검증과 Redis Lua 처리량 비교
- 500명 이상 단계에서 좌석 선점 p90/p95/p99와 HikariCP 상태 확인