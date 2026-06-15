# Seat Rush Frontend

실시간 티켓 예매 흐름을 직접 실행하는 React 기반 MVP입니다.

## 실행

API Gateway와 백엔드 서비스를 먼저 실행한 후 다음 명령을 사용합니다.

```powershell
npm install
npm run dev
```

기본 접속 주소는 `http://localhost:5173`입니다.

## 환경변수

API Gateway 주소를 변경할 때 `.env.local`에 다음 값을 설정합니다.

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 주요 흐름

공연 선택 → 회차 선택 → 대기열 → 좌석 선택 → 예매 → Mock 결제 → 결과 확인
