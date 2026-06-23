# 예매 생성 내 중복 예매 검증 제거 최적화

## 기준선

[좌석 선점 연장 최적화 후 실제 예매 생성 사용자 100명 결과](../load-test-results/2026-06-23-k6-reservation-create-100-users-after-hold-extend-optimization/README.md)에서 예매 생성 API p95 응답 시간은 약 1,083ms였고, Ticket Service 내부 Facade 처리 시간은 p95 기준 522ms였습니다.

내부 구간 계측 결과, 전체 처리 시간의 약 40%에 해당하는 202ms가 중복 예매 여부를 확인하기 위해 DB를 SELECT 조회하는 중복 예매 검증(Precheck 166ms, Transaction Check 36ms)에 소비되는 것으로 확인되었습니다.

## 기존 구조의 문제

### 불필요한 중복 DB SELECT 조회 발생

Java의 `ReservationFacade`와 `ReservationService`는 예매 중복 생성을 방지하기 위해 다음과 같이 총 2회의 DB SELECT 조회를 추가로 유발했습니다.

1. 트랜잭션 진입 전: `ReservationFacade`에서 `reservationRepository.existsByHoldId`로 DB를 1차 사전 조회 (Precheck)
2. 트랜잭션 진입 후: `ReservationService`에서 동일한 `existsByHoldId` 쿼리로 2차 최종 검사 (Transaction Check)

이러한 사전 조회(Select-Before-Insert) 방식은 동시성 환경에서 두 스레드가 동시에 조회를 성공한 뒤 동시에 삽입을 시도할 때 발생하는 레이스 컨디션(Race Condition)을 완전히 예방하지 못하며, 결국 예외 처리가 강제됩니다. 또한, 대규모 트래픽 하에서 DB 커넥션 풀을 과도하게 오래 점유하고 불필요한 네트워크 왕복 지연을 일으키는 주요 병목 지점이었습니다.

## 적용한 개선

### existsByHoldId 조회 제거 및 UNIQUE 제약 조건 활용 (Optimistic Insert)

사전 조회 목적의 `existsByHoldId` DB SELECT 호출을 모두 제거했습니다. 대신, `reservations` 테이블의 `hold_id` 컬럼에 적용되어 있는 UNIQUE 제약 조건(`uk_reservations_hold`)을 100% 활용하도록 개선했습니다.

1. DB에 저장을 시도하는 `saveAndFlush` 시점에 중복된 `hold_id`가 있을 경우 발생되는 `DataIntegrityViolationException`을 Catch합니다.
2. 예외 발생 시 기존 사전 조회 시와 동일한 `RESERVATION_ALREADY_EXISTS` 에러 코드로 번역하여 응답합니다.
3. 이를 통해 99.9% 이상의 정상 예매 생성 트래픽 전체에서 DB SELECT 조회 2회를 완전히 단축하고, 중복 요청에 대한 거부 검증 정합성도 인덱스 수준에서 안전하게 보장합니다.

## 재측정 결과

*배포 및 부하 테스트를 구동한 뒤 실측 수치를 기록했습니다.*

| 지표 | 개선 전 (중앙값) | 개선 후 (중앙값) | 변화 |
| --- | ---: | ---: | --- |
| 예매 생성 API p95 | 1,083 ms | 1,064 ms | 19 ms (1.8% 단축) |
| Ticket Service Facade p95 | 522 ms | 418 ms | 104 ms (19.9% 단축) |
| 중복 예매 Precheck p95 | 166 ms | 제거됨 | N/A |
| 중복 예매 Transaction Check p95 | 36 ms | 제거됨 | N/A |

## 재측정 항목

- 예매 생성 API 호출 시간 p90/p95/p99
- Ticket Service Facade 전체 p95 소요 시간
- 중복 예매 검증 구간(Precheck / Transaction Check) 호출 제거에 따른 쿼리 수 감소 검증
- HikariCP 커넥션 획득 지연 시간 및 active/pending 커넥션 수
- 100명 전원 예매 성공률 및 HTTP 실패율
