# Seat Rush k6 성능 테스트

이 디렉터리는 서버 성능 측정을 위한 k6 테스트 스크립트를 관리합니다.

프론트엔드 경쟁 모드나 `virtual-user-generator`는 사용자 체험을 보여주는 데모 기능이고,
성능 테스트와 병목 분석은 k6로 수행합니다.

## 준비

```bash
k6 version
```

Windows에서는 winget으로 설치할 수 있습니다.

```powershell
winget install k6.k6
```

## 공통 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:8080` | API Gateway 주소 |
| `USERS` | `100` | 동시 사용자 수 |
| `COUNTDOWN_SECONDS` | `60` | 연습 티켓 오픈까지 대기 시간 |
| `SEAT_LAYOUT_ID` | `1` | 연습에 사용할 좌석 배치 ID |
| `POLL_INTERVAL_SECONDS` | `2` | 대기열/예매 상태 조회 주기 |
| `MAX_POLL_COUNT` | `120` | polling 최대 횟수 |
| `JOIN_AFTER_OPEN_MILLIS` | `300` | 오픈 직후 진입 지연 시간 |
| `SEAT_HOLD_RETRY_COUNT` | `5` | 좌석 선점 충돌 시 재시도 횟수 |
| `MAX_SEATS_PER_USER` | `4` | 사용자당 최대 선점 좌석 수 |
| `ACCOUNT_POOL_FILE` | 가상 사용자 계정 풀 파일 | 사전 준비한 테스트 계정 정보 파일 |
| `ACCOUNT_PREPARATION_CONCURRENCY` | `20` | 측정 전 토큰 갱신 동시성 |
| `SETUP_TIMEOUT` | `10m` | 테스트 계정 토큰 준비 제한 시간 |
| `PAYMENT_SUCCESS_PERCENT` | `100` | 결제 성공 비율 |
| `PAYMENT_FAILURE_PERCENT` | `0` | 결제 실패 비율 |
| `USER_PREFIX` | 시나리오별 기본값 | 테스트 계정 email prefix |
| `USER_PASSWORD` | `Password1234!` | 테스트 계정 비밀번호 |

## 대기열 오픈 성능 테스트

티켓 오픈 시점에 여러 사용자가 동시에 대기열에 진입하고, 입장 가능 상태가 되면 entryToken을 발급받는 시나리오입니다.

```powershell
$env:BASE_URL="https://<gateway-domain>"
$env:USERS="100"
$env:COUNTDOWN_SECONDS="60"

k6 run .\load-test\k6\scripts\practice-queue-open.js
```

## 예매 전체 흐름 성능 테스트

대기열 진입부터 좌석 조회, 좌석 선점, 예매 생성, Mock 결제 완료까지 수행합니다.

기준선 성능 테스트에서는 결제 실패와 중간 이탈을 섞지 않고, 모든 사용자가 정상 예매 완료까지 진행하도록 설정합니다.
테스트 계정의 로그인과 토큰 갱신은 오픈 시각 전 준비 단계에서 제한된 동시성으로 처리합니다.
따라서 대기열 진입 이후의 지표에는 로그인 부하가 섞이지 않습니다.

```powershell
$env:BASE_URL="https://<gateway-domain>"
$env:USERS="100"
$env:COUNTDOWN_SECONDS="60"
$env:PAYMENT_SUCCESS_PERCENT="100"
$env:PAYMENT_FAILURE_PERCENT="0"
$env:MAX_SEATS_PER_USER="4"

k6 run .\load-test\k6\scripts\practice-reservation-flow.js
```

결과를 JSON으로 저장하려면:

```powershell
k6 run `
  --summary-export .\docs\load-test-results\k6-reservation-flow-summary.json `
  .\load-test\k6\scripts\practice-reservation-flow.js

powershell -ExecutionPolicy Bypass -File .\load-test\k6\scripts\sanitize-k6-summary.ps1 `
  -SummaryPath .\docs\load-test-results\k6-reservation-flow-summary.json
```

`setup()` 데이터에는 측정 전 갱신한 access token이 포함될 수 있으므로, 요약 파일을
보관하거나 커밋하기 전 반드시 정리 스크립트를 실행합니다.

## 테스트 순서

각 본 측정 전에는 동일 시나리오를 10명, 5초 카운트다운으로 한 번 실행합니다. 워밍업 결과는
문서화하지 않고, Kafka lag와 진행 중인 요청이 안정화된 뒤 본 측정을 시작합니다.

```powershell
$env:USERS="10"
$env:COUNTDOWN_SECONDS="5"
k6 run --insecure-skip-tls-verify .\load-test\k6\scripts\practice-reservation-flow.js
```

본 측정은 아래 순서로 늘립니다.

```text
100 -> 500 -> 1,000 -> 3,000 -> 5,000 -> 10,000 -> 100,000
```

## 해석 기준

- `http_req_failed`: 전체 HTTP 실패율
- `http_req_duration`: 전체 HTTP 응답 시간
- `http_req_duration{name:...}`: API 구간별 응답 시간
- `practice_reservation_flow_duration_ms`: 전체 예매 흐름 소요 시간
- `reservation_confirmed`: 예매 확정 수
- `payment_failed`: 결제 실패 수
- `user_abandoned`: 중도 이탈 수

기준선 테스트에서는 `payment_failed`와 `user_abandoned`가 0이어야 합니다.
