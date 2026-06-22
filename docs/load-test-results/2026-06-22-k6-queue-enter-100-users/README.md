# 대기열 입장 토큰 발급 세부 계측 100명

## 목적

동시 사용자가 대기열 입장 토큰을 발급받는 구간을 Gateway, JWT 서명, Redis 원자 처리로 나누어 측정합니다.

## 환경과 조건

- 대상: 배포 EC2 `t4g.large`, HTTPS API Gateway 경유
- 시나리오: 연습 세션 생성 후 대기열 진입, entryToken 발급, 좌석 선점, 예매, Mock 결제 완료
- 사용자 수: 100명
- 좌석 배치: `seatLayoutId=1`
- 워밍업: 10명, 오픈 대기 5초, 전체 예매 흐름 1회
- 본 측정: 오픈 대기 5초, 전체 예매 흐름 3회
- 계정 accessToken: 측정 시작 전 `setup()` 단계에서 준비
- Prometheus 측정 창: 대기열 오픈 시각부터 30초

## 핵심 결과

| 실행 | 예매 확정 | HTTP 실패율 | entryToken 발급 p90 (ms) | entryToken 발급 p95 (ms) | entryToken 발급 p99 (ms) |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 100 / 100 | 0% | 1,380 | 1,473 | 1,529 |
| 2 | 100 / 100 | 0% | 1,403 | 1,488 | 1,611 |
| 3 | 100 / 100 | 0% | 943 | 974 | 1,027 |
| 중앙값 | 100 / 100 | 0% | 1,380 | 1,473 | 1,529 |

실행 1, 2는 `practice.queue.enter` p95 목표인 1초를 초과해 k6가 임계치 실패 종료 코드로 끝났습니다. HTTP 요청 실패나 예매 실패는 없었으므로, 이는 시나리오 오류가 아니라 응답 시간 기준 미달을 의미합니다.

## 구간별 p95

| 구간 | 실행 1 (ms) | 실행 2 (ms) | 실행 3 (ms) | 중앙값 (ms) |
| --- | ---: | ---: | ---: | ---: |
| API Gateway `queue.enter` | 1,404 | 1,342 | 796 | 1,342 |
| Queue Service 전체 `entry_token.issue` | 1,395 | 1,133 | 708 | 1,133 |
| JWT 생성·RS256 서명 `entry_token.issue.sign` | 197 | 194 | 283 | 197 |
| Redis 원자 처리 `entry_token.issue.redis` | 408 | 358 | 201 | 358 |

각 행은 같은 요청의 시간을 더한 값이 아니라, 해당 구간 전체 요청의 p95입니다. 따라서 p95끼리 단순 합산하거나 Gateway 시간에서 내부 p95를 빼서 정확한 비용을 계산할 수는 없습니다.

## Redis 관련 지표

| 지표 | 실행 1 | 실행 2 | 실행 3 | 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| Queue Service Lettuce Lua 평균 (ms) | 33.49 | 21.39 | 11.76 | 21.39 |
| Queue Redis Lua 서버 실행 평균 (ms) | 0.042 | 0.050 | 0.052 | 0.050 |
| Queue Service JVM GC pause 합계 (ms) | 127.24 | 26.90 | 38.28 | 38.28 |

Redis 서버의 Lua 실행 시간은 매우 짧지만 애플리케이션의 Redis 원자 처리 p95는 201~408ms입니다. 따라서 현재 병목 후보는 Lua 연산 자체보다 동시 요청이 몰릴 때의 클라이언트 연결·명령 대기와 Redis 요청 순서 대기입니다.

## 해석과 다음 확인 항목

- JWT 서명은 중앙값 p95가 197ms로 무시하기 어려운 비용이지만, 현재 전체 지연을 혼자 설명하지는 못합니다.
- `entry_token.issue.redis`는 Redis Lua 실행뿐 아니라 클라이언트에서 요청을 보내고 응답을 받을 때까지의 시간을 포함합니다.
- 연습 모드에서는 토큰 발급 뒤 `expirePracticeSessionKeys()`가 5개 키에 TTL을 갱신합니다. 이 비용은 현재 Redis 원자 처리 타이머 밖에 있으므로 다음 측정에서 별도 계측해야 합니다.
- 다음 개선 전에는 토큰 발급 요청당 Redis 명령 수와 연습 세션 TTL 갱신 시간을 분리해 기록한 뒤, TTL 갱신을 세션 생성 또는 키 최초 생성 시점으로 옮길 수 있는지 검토합니다.

## 원본 결과

- [실행 1 요약](./runs/run-1/summary.json), [세부 지표](./runs/run-1/queue-enter-metrics.json)
- [실행 2 요약](./runs/run-2/summary.json), [세부 지표](./runs/run-2/queue-enter-metrics.json)
- [실행 3 요약](./runs/run-3/summary.json), [세부 지표](./runs/run-3/queue-enter-metrics.json)
