# Spring WebFlux 가상 사용자 생성기

가상 사용자 생성기는 로컬 PC에서 실행하고 실제 요청은 배포된 Seat Rush API Gateway로 전송합니다.

```text
로컬 PC
  Spring WebFlux 가상 사용자 생성기 :8085
        |
        | HTTPS
        v
배포 서버
  API Gateway -> Queue/Ticket/Payment Service
```

## IntelliJ 실행 설정

실행 클래스는 `VirtualUserGeneratorApplication`입니다.

IntelliJ Run Configuration의 `Environment variables`에 배포 주소를 설정합니다.
Elastic IP가 `1.2.3.4`라면:

```env
VIRTUAL_USER_GATEWAY_BASE_URL=https://1-2-3-4.sslip.io
```

필요한 환경변수 목록과 기본값은 `.env.example`에서 확인할 수 있습니다.
애플리케이션 실행 후 메인 프론트엔드에서 경쟁 모드로 진입합니다.

```text
http://localhost:5173
```

화면에서 `경쟁 모드`를 선택하고 배포 서버의 공연과 회차를 설정합니다.

- 배포 서버에 등록된 공연
- 경쟁할 회차
- 가상 사용자 수
- 행동별 사용자 비율

회차를 선택하면 해당 회차의 `bookingOpenAt`이 요청 시작 시각으로 자동 설정됩니다. 생성기는 오픈 전에 계정과 토큰을 준비하고, 실제 오픈 시각부터 `joinJitterMillis` 범위 안에서 배포 서버의 대기열 API로 요청을 전송합니다.

경쟁을 미리 예약해도 즉시 로그인하지 않습니다. 기본적으로 오픈 10분 전부터 부족한 계정 생성과 토큰 준비를 시작하므로 오픈 전에 access token이 만료되는 것을 방지합니다. 준비 시작 시간은 `VIRTUAL_USER_PREPARATION_LEAD_TIME`으로 조정할 수 있습니다.

## 가상 사용자 계정 풀

가상 사용자 계정은 실행마다 새로 만들지 않고 다음 파일에 누적 저장합니다.

```text
data/virtual-user-accounts.json
```

- 저장된 계정 10,000명, 요청 인원 5,000명: 기존 5,000명 사용
- 저장된 계정 10,000명, 요청 인원 10,000명: 기존 10,000명 사용
- 저장된 계정 10,000명, 요청 인원 12,000명: 초과한 2,000명만 회원가입

access token과 만료 시각도 함께 저장합니다. 경쟁 종료까지 유효한 토큰은 재사용하고, 만료되었거나 유효 시간이 부족한 계정만 다시 로그인합니다.

계정 풀 파일은 `.gitignore` 대상이며 저장소에 올라가지 않습니다. 배포 서버의 실제 사용자 데이터는 Ticket Service DB에 저장되고, 로컬 파일은 해당 계정과 토큰을 재사용하기 위한 목록입니다.

## API

생성기는 별도 화면을 제공하지 않으며 메인 프론트엔드의 경쟁 모드에서 다음 API를 사용합니다.

```http
POST http://localhost:8085/api/competitions
Content-Type: application/json
```

```json
{
  "scheduleId": 1,
  "virtualUsers": 100,
  "startAt": "2026-06-15T21:00:00+09:00",
  "prepareConcurrency": 20,
  "joinJitterMillis": 5000,
  "behaviors": {
    "abandonQueue": 5,
    "abandonAfterEntry": 5,
    "abandonAfterHold": 10,
    "paymentFailure": 10,
    "paymentSuccess": 70
  }
}
```

상태 조회:

```http
GET http://localhost:8085/api/competitions
```

중지:

```http
DELETE http://localhost:8085/api/competitions
```

## 역할

- 오픈 시각에 배포 서버 대기열로 비동기 요청 전송
- 대기 순번 Polling
- entryToken 발급
- 좌석 조회와 동시 선점
- 예매 생성 및 Mock 결제
- 대기·입장·선점 단계 이탈 사용자 재현
- SSE 기반 실시간 진행 상태 제공

이 기능은 실제 사용자와 가상 사용자의 경쟁 흐름을 보여주기 위한 것입니다. 정확한 처리량과 서버 한계 측정은 별도의 부하 테스트에서 진행합니다.
