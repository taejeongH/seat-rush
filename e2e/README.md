# 개발 환경 E2E 검증

로컬 인프라와 모든 백엔드 서비스를 실행한 뒤, 실제 사용자 예매 흐름을 API Gateway를 통해 검증합니다.

## 사전 조건

- MySQL에 `seat_rush`, `seat_rush_payment` 데이터베이스가 실행 중이어야 합니다.
- Redis, Kafka는 로컬 Docker Compose로 실행합니다.
- API Gateway와 네 개의 애플리케이션이 실행 중이어야 합니다.
- 정상 로그인으로 발급받은 `ADMIN` 사용자의 accessToken이 필요합니다.

```powershell
docker compose -f infra/docker-compose.local.yml up -d
```

| 애플리케이션 | 포트 |
| --- | --- |
| API Gateway | `8080` |
| Ticket Service | `8081` |
| Queue Service | `8082` |
| Payment Service | `8083` |
| Notification Consumer | `8084` |

## 실행

관리자 accessToken을 현재 터미널의 환경변수로 설정한 후 저장소 루트에서 실행합니다.

```powershell
$env:E2E_ADMIN_ACCESS_TOKEN="관리자-accessToken"
.\e2e\verify-local.ps1
```

Git Bash에서는 다음과 같이 실행합니다.

```bash
E2E_ADMIN_ACCESS_TOKEN="관리자-accessToken" \
  powershell -ExecutionPolicy Bypass -File ./e2e/verify-local.ps1
```

다른 회차를 사용하려면 공연 ID와 회차 ID를 함께 지정합니다.

```powershell
.\e2e\verify-local.ps1 -ConcertId 1 -ScheduleId 2
```

스크립트는 회차를 일시적으로 오픈하고 원래 상태로 복구합니다. 성공 시 선택한 좌석과 예매 결과는 실제 로컬 데이터에 남습니다.

## 검증 흐름

1. 공연 API 및 Gateway 연결 확인
2. 테스트 사용자 회원가입과 로그인
3. 회차 오픈 및 Queue Service 동기화
4. 대기열 진입과 entryToken 발급
5. 좌석 조회와 선점
6. 예매 생성과 결제 요청
7. Mock 결제 성공 처리
8. Kafka 결제 결과 소비와 예매 확정
9. 좌석 `RESERVED` 상태 확인
