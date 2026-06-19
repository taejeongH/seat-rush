# E2E 및 동시성 검증 계획

## 목적

전체 티켓 예매 흐름과 주요 동시성 제어가 실제 서비스 실행 환경에서도 정상 동작하는지 검증합니다.

검증은 API Gateway를 기준으로 수행합니다. 서비스 내부 메서드 호출이 아니라 실제 사용자가 접근하는 HTTP 경로, Kafka 비동기 이벤트, Redis 원자 연산이 함께 동작하는지를 확인합니다.

## 자동 검증 시나리오

`e2e/verify-local.ps1`에서 다음 항목을 자동으로 검증합니다.

| 시나리오 | 검증 내용 |
| --- | --- |
| 인증 보호 | 인증 없이 내 정보 조회 시 401 또는 403 반환 |
| 회원가입/로그인 | 테스트 사용자 생성 후 accessToken 발급 |
| 회차 오픈 | 관리자 토큰으로 대상 회차를 임시 `BOOKING_OPEN` 상태로 변경 |
| 대기열 진입 | 회차별 대기열 진입 및 순번 발급 |
| 중복 대기열 진입 | 동일 사용자가 다시 진입해도 같은 순번 유지 |
| 입장 토큰 발급 | 입장 가능 상태에서 entryToken 발급 |
| 동일 좌석 동시 선점 | 두 사용자가 같은 좌석을 동시에 선점하면 하나만 성공 |
| 예매 생성 | holdId 기반 `PENDING_PAYMENT` 예매 생성 |
| 중복 예매 방지 | 동일 holdId로 예매를 다시 생성하면 실패 |
| 결제 요청 | Ticket Service에서 결제 요청 이벤트 발행 |
| Mock 결제 완료 | Payment Service가 결제 요청 이벤트를 소비한 뒤 결제 완료 |
| 중복 결제 완료 | 같은 결제 완료 요청을 반복해도 최종 상태 유지 |
| 결제 결과 연동 | 결제 결과 Kafka 이벤트 소비 후 예매 `CONFIRMED` 반영 |
| 좌석 상태 반영 | 결제 성공 후 좌석 `RESERVED` 반영 |
| 알림 이벤트 소비 | Notification Consumer가 예매 완료 알림을 소비하고 Redis dedup key를 `COMPLETED`로 저장 |

## 실행 명령

```powershell
$env:E2E_ADMIN_ACCESS_TOKEN="관리자-accessToken"
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1
```

배포 환경을 대상으로 실행할 때는 Gateway URL을 지정합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1 `
  -GatewayUrl "https://43.202.76.93.sslip.io" `
  -AdminAccessToken "관리자-accessToken"
```

## 수동 또는 별도 테스트로 검증할 항목

다음 항목은 자동 E2E 스크립트에서 직접 장애를 만들면 실행 환경에 영향을 줄 수 있으므로, 별도 테스트나 제한된 환경에서 검증합니다.

| 시나리오 | 권장 검증 방식 |
| --- | --- |
| Kafka 재시도 | 컨슈머 일시 중단 또는 잘못된 메시지 투입 후 재시도 로그 확인 |
| DLT 처리 | 재시도 초과 메시지가 `*-dlt` 토픽으로 이동하는지 Kafka UI에서 확인 |
| 예매 만료와 결제 성공 경합 | 백엔드 통합 테스트에서 상태 전환 정책 검증 |
| 대규모 동시성 | 부하 테스트 단계에서 가상 사용자 생성기로 검증 |

## 확인할 Kafka 토픽

| 토픽 | 역할 |
| --- | --- |
| `payment-request-v1` | Ticket Service가 Payment Service에 결제 요청 전달 |
| `payment-result-v1` | Payment Service가 Ticket Service에 결제 결과 전달 |
| `entry-slot-release-v1` | Ticket Service가 Queue Service에 입장 슬롯 반환 요청 |
| `reservation-confirmed-v1` | 예매 확정 알림 이벤트 |
| `payment-failed-v1` | 결제 실패 알림 이벤트 |
| `*-dlt` | 소비 실패 후 재시도 초과 메시지 보관 |

## 알림 검증 방식

현재 알림은 실제 메일/SMS 발송이 아니라 Mock 발송과 로그 기록으로 처리합니다. 따라서 E2E에서는 외부 수신함 대신 Notification Consumer의 중복 발송 방지 저장소를 관측합니다.

정상적으로 알림 이벤트가 소비되면 Notification Redis에 다음 형태의 key가 저장됩니다.

```text
notification:event:{eventId} = COMPLETED
```

`verify-local.ps1`은 테스트 시작 전 `COMPLETED` key 개수를 기록하고, 예매 확정 후 개수가 증가하는지 확인합니다. 이 검증은 Docker로 실행 중인 로컬 Notification Redis에 접근할 수 있을 때 수행됩니다.

## 결과 기록 양식

테스트 실행 후 다음 내용을 PR 또는 이슈 코멘트에 남깁니다.

```md
## E2E 검증 결과

- 실행 환경:
- Gateway URL:
- 대상 공연/회차:
- 실행 시간:
- 결과:
- 생성된 reservationId:
- 생성된 paymentId:
- 비고:
```
