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

*머지 후 원격 배포 및 부하 테스트를 구동한 뒤 실측 수치를 기록할 예정입니다.*

| 지표 | 개선 전 | 개선 후 (예정) | 변화 |
| --- | ---: | ---: | ---: |
| 예매 생성 API p95 | 762 ~ 997 ms | *TBD* | *TBD* |
| 좌석 선점 연장 (`holdExtend`) p95 | 330 ms | *TBD* | *TBD* |
| Redis Lua 실행 시간 (`holdExtendRedis`) p95 | 171 ms | *TBD* | *TBD* |

## 재측정 항목

- 예매 생성 API 호출 시간 p90/p95/p99
- 좌석 선점 TTL 연장 (`holdExtend`) 내부 구간 p95 소요 시간
- Lettuce Lua 실행 시간 (`holdExtendRedis`) 및 Redis Lua 명령 수
- HikariCP 커넥션 획득 지연 시간 및 active/pending 커넥션 수
- 100명 전원 예매 성공률 및 HTTP 실패율

동일한 10명 워밍업과 100명 3회 실행 조건으로 최적화 이전 기준선과 비교합니다.
