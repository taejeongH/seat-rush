# 대기열 polling 및 TTL 갱신 최적화

## 기준선

[대기열 전용 입장 경로 100명 세부 계측](../load-test-results/2026-06-22-k6-queue-open-100-users/README.md)에서 대기열 진입, 순번 조회, entryToken 발급 p95 중앙값은 각각 1,473ms, 1,252ms, 1,115ms였습니다.

## 기존 구조의 문제

### 순번 조회마다 사용자 세션 갱신

`GET /queues/me` Lua Script는 순번을 조회할 때마다 사용자별로 다음 쓰기를 수행했습니다.

- `PSETEX`: 사용자 대기 세션 30초 연장
- `ZADD`: 사용자 만료 시각 갱신

1초 polling 기준으로 사용자 1명당 초당 2회 Redis 쓰기가 발생합니다.

### 요청마다 공통 연습 세션 TTL 갱신

연습 모드의 `join`, `me`, `heartbeat`, `enter` 이후에는 다음 공통 키 5개에 별도 Redis `EXPIRE` 요청을 보냈습니다.

- `waiting`: 사용자 ID와 대기열 진입 순번
- `sequence`: 다음 대기 순번을 만들기 위한 증가값
- `scheduleState`: 예매 오픈·마감 시각과 회차 상태
- `activeEntries`: entryToken ID와 입장 권한 만료 시각
- `sessionExpirations`: 사용자 ID와 개인 대기 세션 만료 시각

특히 `me` polling이 많을 때 대기열 조회와 무관한 TTL 갱신 요청이 Redis 처리량을 차지했습니다.

## 적용한 개선

### 순번 조회와 사용자 세션 heartbeat 분리

- `GET /queues/me`는 순번·입장 가능 상태 조회와 만료 사용자 정리만 수행합니다.
- 사용자 세션 30초 TTL은 기존 `POST /queues/heartbeat`만 갱신합니다.
- 프론트엔드와 가상 사용자 생성기는 2초 polling, 10초 heartbeat를 사용합니다.
- k6 시나리오도 같은 간격으로 heartbeat를 전송합니다.

사용자 이탈 감지 기준인 30초 TTL은 유지하면서, 사용자별 Redis 쓰기 빈도는 1초마다 2회에서 10초마다 2회로 줄어듭니다.

### 공통 연습 세션 TTL 갱신 제한

- 연습 세션 생성 시 `scheduleState`에 데이터 TTL을 부여합니다.
- `join`과 `enter` Lua Script는 새로 생성된 공통 키에만 최초 TTL을 부여합니다.
- heartbeat Lua Script는 `scheduleState`의 남은 TTL을 확인합니다.
- 남은 TTL이 25분 이하일 때만 공통 키 TTL을 다시 30분으로 연장합니다.

기본 설정은 데이터 TTL 30분, 갱신 최소 간격 5분입니다. 사용자가 계속 활동하면 세션은 5분 단위로 연장되므로 30분을 넘는 연습도 유지됩니다. 모든 사용자가 떠나면 heartbeat가 멈추고 마지막 연장 후 30분 뒤 Redis 데이터가 정리됩니다.

## 재측정 항목

- 대기열 진입·순번 조회·entryToken 발급 p90/p95/p99
- Queue Service `queue.position`, `queue.heartbeat`, `entry_token.issue` 지표
- Lettuce Lua 명령 p95와 Redis Lua 명령 수
- 100명 전원 entryToken 발급 성공률과 HTTP 실패율

동일한 10명 워밍업과 100명 3회 실행 조건으로 기존 기준선과 비교합니다.
