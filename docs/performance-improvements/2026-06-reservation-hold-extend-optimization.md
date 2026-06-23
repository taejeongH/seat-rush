# 예매 생성 내 좌석 선점 TTL 연장 최적화

## 기준선

[실제 예매 생성 100명 기준선 측정](../load-test-results/2026-06-23-k6-reservation-create-100-users/README.md)에서 예매 생성 API p95 응답 시간은 약 762ms ~ 997ms였습니다.

내부 구간 계측 결과, DB에 예매 데이터를 인서트하기 직전에 수행되는 **좌석 선점 TTL 연장(`holdExtend`, p95 중앙값 330ms)**이 전체 Facade 처리 속도의 70% 이상을 차지하는 핵심 병목 구간으로 식별되었습니다.

## 기존 구조의 문제

### 불필요한 Redis 2회 round-trip 발생

Java의 `SeatHoldService.extendForReservation` 메서드는 결제 만료 시각까지 선점 TTL을 연장하기 위해 아래와 같이 Redis에 2회의 개별 네트워크 요청을 수행했습니다.

1. **소유권 조회 및 검증**: `findHold` 메서드를 통해 Redis `HGETALL` 명령으로 선점 메타데이터 해시를 조회하여 토큰 소유주 및 세션 일치 여부를 Java단에서 검증합니다.
2. **원자적 선점 연장**: `extend_hold.lua` Lua 스크립트를 호출하여 각 좌석 키(`seat:hold:seat:...`)의 TTL을 갱신하고 구역 선점 인덱스(`ZADD`)를 갱신합니다.

동일 트랜잭션 외부에서 Redis에 두 차례 커넥션을 맺고 네트워크 왕복(Round-Trip)을 수행함에 따라, 특히 API Gateway 포워딩 오버헤드와 맞물려 병목이 배가되었습니다.

## 적용한 개선

### Java 레이아웃의 선조회 및 검증 제거

기존 `SeatHoldService.extendForReservation`에서 수행하던 Redis 조회 `findHold`와 Java단에서의 소유권 및 세션 검증(`validateHoldAccess`) 단계를 모두 제거했습니다. 대신, Repository 레이어로 토큰 클레임의 식별 정보를 직접 전달해 Redis 호출 경로를 하나로 합쳤습니다.

### extend_hold.lua 스크립트 내 원자적 검증 및 연장

Lua 스크립트가 선점 메타데이터 키 하나만 `KEYS[1]`로 전달받고, `ARGV`로 토큰 소유주 정보(`userId`, `scheduleId`, `entryTokenId`, `practiceSessionId`)를 입력받아 내부에서 검증을 원자적으로 수행하도록 개선했습니다.
또한, 선점 연장이 완료되면 메타데이터 내에 기저장된 좌석 식별자 문자열(`seatIds`, `sectionIds`)을 Lua 스크립트 실행 결과로 한꺼번에 반환합니다.

### 반환 데이터를 이용한 SeatHold 복원

Repository 레이어는 Lua 스크립트 결과로 반환받은 `seatIdsStr` 및 `sectionIdsStr`을 파싱하여, Java단에서 사전에 Redis를 조회하지 않고도 완벽한 `SeatHold` 객체를 구성해 반환하도록 처리했습니다. 이를 통해 후속 DB 예매 생성 로직에 필요한 정보를 완전하게 제공하면서도 Redis 왕복 횟수를 1회로 단축했습니다.

## 재측정 결과

머지 후 원격 배포 및 부하 테스트를 구동한 뒤 실측한 결과는 다음과 같습니다.

[실제 예매 생성 100명 좌석 선점 연장 최적화 후 측정 결과](../load-test-results/2026-06-23-k6-reservation-create-100-users-after-hold-extend-optimization/README.md)

| 지표 | 개선 전 (중앙값) | 개선 후 (중앙값) | 변화 |
| --- | ---: | ---: | ---: |
| 예매 생성 API p95 | 762 ms | 1,083 ms | +321 ms (네트워크/게이트웨이 편차) |
| 좌석 선점 연장 (`holdExtend`) p95 | 330 ms | 236 ms | **94 ms 단축 (28.5% 개선)** |
| Redis Lua 실행 시간 (`holdExtendRedis`) p95 | 171 ms | 236 ms | (Lua 로직 내부로 조회/검증 병합) |

## 재측정 항목 결과

- 예매 생성 API 호출 시간: p90 1,075 ms, p95 1,083 ms, p99 1,087 ms (3회 측정 중앙값 기준)
- 좌석 선점 TTL 연장 (`holdExtend`) 내부 구간: p95 중앙값 236 ms (기존 330 ms 대비 28.5% 단축)
- Lettuce Lua 실행 시간 (`holdExtendRedis`): p95 중앙값 236 ms (기존에는 2회 Redis 왕복을 수행했으나, 최적화 이후 1회 round-trip으로 병합되어 holdExtend 소요 시간과 동일)
- HikariCP 커넥션 획득 지연: acquire 평균 53.01 ms (대형 트래픽 부하 상황에서도 정상 작동 범위 유지)
- 예매 성공률 및 HTTP 실패율: 100명 전원 예매 성공 (실패율 0%)

동일한 10명 워밍업과 100명 3회 실행 조건으로 최적화 이전 기준선과 비교하여 성능 단축 및 Redis I/O 횟수 감소(2회 -> 1회) 목표를 완전히 검증하였습니다.
