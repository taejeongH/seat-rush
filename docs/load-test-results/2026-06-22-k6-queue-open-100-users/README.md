# 대기열 전용 입장 경로 100명 세부 계측

## 목적

좌석 조회·선점·예매를 제외하고 대기열 진입, 순번 조회, entryToken 발급만 수행해 Queue Service 경로의 지연을 분리 측정합니다.

## 환경과 조건

- 대상: 배포 EC2 `t4g.large`, HTTPS API Gateway 경유
- 시나리오: 연습 세션 생성 후 대기열 진입, 순번 조회, entryToken 발급
- 사용자 수: 100명
- 좌석 배치: `seatLayoutId=1`
- 계정 accessToken: 측정 시작 전 `setup()` 단계에서 준비
- 워밍업: 10명, 오픈 대기 5초, 대기열 전용 시나리오 1회
- 본 측정: 오픈 대기 5초, 대기열 전용 시나리오 3회
- Prometheus 측정 창: 대기열 오픈 시각부터 30초

## 핵심 API p90

| API | 실행 1 (ms) | 실행 2 (ms) | 실행 3 (ms) | 중앙값 (ms) |
| --- | ---: | ---: | ---: | ---: |
| 대기열 진입 | 1,630 | 965 | 1,419 | 1,419 |
| 내 순번 조회 | 1,791 | 923 | 1,068 | 1,068 |
| entryToken 발급 | 1,784 | 1,040 | 864 | 1,040 |

## 핵심 API p95

| API | 실행 1 (ms) | 실행 2 (ms) | 실행 3 (ms) | 중앙값 (ms) |
| --- | ---: | ---: | ---: | ---: |
| 대기열 진입 | 1,969 | 1,006 | 1,473 | 1,473 |
| 내 순번 조회 | 1,818 | 1,034 | 1,252 | 1,252 |
| entryToken 발급 | 1,855 | 1,115 | 895 | 1,115 |

## 핵심 API p99

| API | 실행 1 (ms) | 실행 2 (ms) | 실행 3 (ms) | 중앙값 (ms) |
| --- | ---: | ---: | ---: | ---: |
| 대기열 진입 | 2,342 | 1,086 | 1,685 | 1,685 |
| 내 순번 조회 | 1,861 | 1,102 | 1,345 | 1,345 |
| entryToken 발급 | 1,923 | 1,231 | 986 | 1,231 |

모든 실행에서 100명 전원이 entryToken을 발급받았고 HTTP 요청 실패는 없었습니다. 다만 각 API의 p95 목표 1초는 실행별로 일부 또는 전부 초과했습니다.

## 내부 구간 p95

| 구간 | 실행 1 (ms) | 실행 2 (ms) | 실행 3 (ms) | 중앙값 (ms) |
| --- | ---: | ---: | ---: | ---: |
| Queue Service 순번 조회 | 1,704 | 552 | 884 | 884 |
| Queue Service entryToken 발급 | 1,708 | 770 | 845 | 845 |
| JWT 생성·RS256 서명 | 451 | 380 | 82 | 380 |
| entryToken Lua 처리 | 388 | 307 | 237 | 307 |
| entryToken 뒤 TTL 갱신 | 1,208 | 347 | 698 | 698 |
| 공통 연습 세션 TTL 갱신 | 1,368 | 356 | 753 | 753 |
| Lettuce Lua 명령 | 137 | 36 | 158 | 137 |

각 행의 p95는 서로 다른 요청 표본의 분위수이므로 단순히 더하거나 빼서 전체 p95를 계산할 수 없습니다.

## 분석

`QueueService.expirePracticeSessionKeys()`와 `EntryTokenService`는 연습 모드 요청마다 `waiting`, `sequence`, `scheduleState`, `activeEntries`, `sessionExpirations`의 TTL을 각각 갱신합니다. 요청 하나당 Redis `EXPIRE` 호출이 5회 추가되는 구조입니다.

대기열 전용 측정에서 이 TTL 갱신 구간 p95가 698~1,368ms로 나타났고, 순번 조회와 entryToken 발급 지연에 모두 포함됩니다. 반면 entryToken Lua 처리 p95 중앙값은 307ms입니다. 따라서 다음 개선은 Redis 연결 수를 먼저 늘리는 것이 아니라, 매 요청 TTL 갱신을 세션 생성·키 최초 생성 시점으로 옮기거나 한 번의 원자 작업으로 합치는 것입니다.

JWT 서명도 중앙값 p95가 380ms로 관찰됐지만, TTL 갱신 구조를 개선한 뒤 동일 조건에서 다시 비교해 판단합니다.

## 원본 결과

- [실행 1 요약](./runs/run-1/summary.json), [세부 지표](./runs/run-1/queue-enter-metrics.json)
- [실행 2 요약](./runs/run-2/summary.json), [세부 지표](./runs/run-2/queue-enter-metrics.json)
- [실행 3 요약](./runs/run-3/summary.json), [세부 지표](./runs/run-3/queue-enter-metrics.json)
