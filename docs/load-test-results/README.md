# 성능 테스트 결과

이 디렉터리는 `k6`로 실행한 성능 테스트의 원본 결과와 분석 문서를 관리합니다.

## 기록 규칙

- 디렉터리 이름: `<YYYY-MM-DD>-k6-<사용자수>-users`
- 정상 시나리오와 실패·이탈 시나리오는 분리해 기록합니다.
- 동일 시나리오를 개선 전후로 비교할 때는 날짜 또는 `-after` 접미사로 구분합니다.
- 각 결과에는 k6 원본 결과, 테스트 조건, 핵심 API p90/p95/p99, 서버 자원 지표와 해석을 포함합니다.

## 예정 단계

| 순서 | 동시 사용자 수 | 상태 |
| --- | ---: | --- |
| 1 | 100 | 측정 완료 |
| 2 | 500 | 예정 |
| 3 | 1,000 | 예정 |
| 4 | 3,000 | 예정 |
| 5 | 5,000 | 예정 |
| 6 | 10,000 | 예정 |
| 7 | 100,000 | 예정 |

## 결과

- [100명·500명·1,000명 예매 연습 단계별 측정 결과](./2026-06-20-k6-100-500-1000.md)
- [좌석 조회 개선 후 100명 사용자 결과](./2026-06-20-k6-after-seat-query-100-users/README.md)
- [좌석 조회 응답 경로 기준선 100명](./2026-06-20-k6-seat-query-path-baseline-100-users/README.md)
- [응답 버퍼링 제거 후 좌석 조회 사용자 100명](./2026-06-21-k6-response-buffer-removed-100-users/README.md)
- [JPA Projection 적용 후 좌석 조회 사용자 100명](./2026-06-21-k6-projection-100-users/README.md)
- [HikariCP 및 Redis hold 세부 계측 사용자 100명](./2026-06-21-k6-hikari-redis-metrics-100-users/README.md)
- [구역별 hold 인덱스 적용 후 사용자 100명](./2026-06-22-k6-section-hold-index-100-users/README.md)
- [대기열 입장 토큰 발급 세부 계측 사용자 100명](./2026-06-22-k6-queue-enter-100-users/README.md)
- [대기열 전용 입장 경로 세부 계측 사용자 100명](./2026-06-22-k6-queue-open-100-users/README.md)
- [대기열 TTL 및 polling 최적화 후 사용자 100명](./2026-06-22-k6-queue-open-100-users-after-ttl-optimization/README.md)
- [분산 좌석 선점 사용자 100명 기준선](./2026-06-22-k6-seat-hold-distributed-100-users/README.md)
- [연습 좌석 검증 조회 제거 후 분산 좌석 선점 사용자 100명](./2026-06-23-k6-seat-hold-layout-validation-100-users/README.md)
- [실제 예매 생성 사용자 100명 기준선](./2026-06-23-k6-reservation-create-100-users/README.md)
- [좌석 선점 연장 최적화 후 실제 예매 생성 사용자 100명](./2026-06-23-k6-reservation-create-100-users-after-hold-extend-optimization/README.md)

## 개선 문서

- [좌석 조회 및 대기열 경로 성능 개선](../performance-improvements/2026-06-seat-query-optimization.md)
- [대기열 polling 및 TTL 갱신 최적화](../performance-improvements/2026-06-queue-polling-ttl-optimization.md)
- [예매 생성 내 좌석 선점 TTL 연장 최적화](../performance-improvements/2026-06-reservation-hold-extend-optimization.md)
