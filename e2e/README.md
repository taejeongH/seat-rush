# 개발 환경 E2E 검증

로컬 또는 배포 환경에서 API Gateway를 통해 전체 예매 흐름과 주요 동시성 제어를 검증합니다.

## 사전 조건

- MySQL, Redis, Kafka가 실행 중이어야 합니다.
- API Gateway, Ticket Service, Queue Service, Payment Service, Notification Consumer가 실행 중이어야 합니다.
- 회차 상태를 임시 변경하기 위해 `ADMIN` 권한 accessToken이 필요합니다.

로컬 인프라는 다음 명령으로 실행합니다.

```powershell
docker compose -f infra/docker-compose.local.yml up -d
```

| 서비스 | 기본 주소 |
| --- | --- |
| API Gateway | `http://localhost:8080` |
| Kafka UI | `http://localhost:8089` |
| Queue Redis | `localhost:6379` |
| Seat Redis | `localhost:6380` |
| Notification Redis | `localhost:6381` |

## 실행 방법

저장소 루트에서 관리자 accessToken을 환경변수로 지정한 뒤 실행합니다.

```powershell
$env:E2E_ADMIN_ACCESS_TOKEN="관리자-accessToken"
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1
```

배포 서버를 대상으로 실행할 때는 Gateway URL을 지정합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1 `
  -GatewayUrl "https://43.202.76.93.sslip.io" `
  -AdminAccessToken "관리자-accessToken"
```

다른 공연/회차를 사용하려면 ID를 함께 지정합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1 `
  -ConcertId 1 `
  -ScheduleId 1
```

## 검증 범위

- 회원가입부터 예매 확정까지 정상 E2E 흐름
- 대기열 진입, 중복 진입, 입장 토큰 발급
- 좌석 조회, 좌석 선점, 동일 좌석 동시 선점 경합
- 동일 holdId 중복 예매 생성 방지
- 결제 요청, Mock 결제 완료, 중복 결제 완료 멱등 처리
- Kafka 결제 결과 이벤트 소비 후 예매 `CONFIRMED` 반영
- 결제 후 좌석 `RESERVED` 반영
- Notification Consumer가 예매 완료 알림 이벤트를 소비하고 Redis dedup key를 `COMPLETED`로 저장했는지 확인

## 결과 해석

스크립트가 성공하면 마지막에 예약 ID, 결제 ID, 좌석 ID와 함께 `E2E verification passed.`가 출력됩니다.

실패하면 어느 단계에서 실패했는지 메시지와 API 응답 본문을 함께 출력합니다. 테스트 도중 변경한 회차 상태는 `finally` 블록에서 원래 값으로 복구합니다.

알림 검증은 로컬 Notification Redis 컨테이너의 `notification:event:*` key 중 `COMPLETED` 상태가 새로 증가했는지 확인합니다. 다른 컨테이너 이름을 사용한다면 다음처럼 지정합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1 `
  -NotificationRedisContainer "seat-rush-local-notification-redis"
```

Docker 컨테이너에 접근할 수 없는 환경에서는 알림 검증을 건너뛸 수 있습니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\e2e\verify-local.ps1 -SkipNotificationCheck
```

## 추가 확인

Kafka 재시도와 DLT는 실제 브로커/컨슈머 장애 상황을 만들어야 하므로 자동 E2E 스크립트에서는 직접 망가뜨리지 않습니다. 장애 검증이 필요하면 컨슈머를 잠시 중단하거나 잘못된 메시지를 투입한 뒤 Kafka UI에서 다음 토픽을 확인합니다.

| 토픽 | 확인 목적 |
| --- | --- |
| `payment-result-v1-dlt` | 결제 결과 이벤트 소비 실패 |
| `entry-slot-release-v1-dlt` | 입장 슬롯 반환 이벤트 소비 실패 |
| `reservation-confirmed-v1-dlt` | 예매 완료 알림 이벤트 소비 실패 |
| `payment-failed-v1-dlt` | 결제 실패 알림 이벤트 소비 실패 |
