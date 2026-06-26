# 고동시성 대응을 위한 OS 커널 및 네트워크 백로그 튜닝

## 목적

1,000 VU 및 3,000 VU의 고부하 상황에서 발생하는 네트워크 연결 유실(드랍) 및 EOF/소켓 거부 오류를 해결하기 위해, EC2 호스트 운영체제와 Docker 컨테이너 내부의 네트워크 스택 및 리스닝 소켓 백로그 설정을 최적화합니다.

## 진행 순서

1. 1,000 VU 부하 테스트 중 Nginx와 API Gateway 간의 연결 종료 오류(EOF)를 확인했습니다.
2. 3,000 VU 부하 테스트 첫 실행 시 대기열 진입 API에서 대량의 소켓 끊김 오류(status=0)가 발생하는 것을 관측했습니다.
3. EC2 호스트 수준의 TCP 리슨 큐 및 SYN 백로그 한도를 조정했습니다.
4. Docker 컨테이너 네트워크 네임스페이스 격리로 인해 발생한 내부 백로그 병목을 해결하기 위해 docker-compose.server.yml 에 sysctls 설정을 추가했습니다.
5. Nginx 및 API Gateway, queue-service 애플리케이션의 리슨 소켓 backlog 파라미터를 명시적으로 확장시켰습니다.

## 문제 분석 (튜닝 전)

### 1,000 VU 동시 진입 시 연결 종료 발생
- 현상: queues/me API 호출 중 일부 요청이 EOF 또는 연결 거부로 실패했습니다. (36건 실패, 실패율 0.149%)
- 원인: 단일 호스트 내에서 순간 유입되는 TCP SYN 패킷이 급증하였으나, 기본 TCP SYN 대기 백로그 큐 크기가 작아 요청이 유실되었습니다.

### 3,000 VU 동시 진입 시 대량 소켓 드랍 발생
- 현상: 대기열 최초 진입(queue/join) 단계에서 2,153건의 요청이 status=0 (네트워크 레벨 연결 거부)으로 실패했습니다. (실패율 14.23%)
- 원인: 
  - 호스트 OS는 튜닝되었으나 Docker 컨테이너 내부 네트워크 네임스페이스의 tcp_max_syn_backlog 한도가 디폴트 값인 512에 고정되어 있었습니다.
  - Nginx 및 Spring Boot 리슨 소켓 바인딩 시 backlog 매개변수가 명시적으로 커스텀 설정되어 있지 않고 기본값(511)으로 제한되어 한계에 도달했습니다.

## 해결 방안 (튜닝 적용 내역)

### 1. EC2 호스트 OS 커널 튜닝
호스트 운영체제 수준에서 대기열 소켓 연결 및 SYN 백로그 한도를 65535로 개방했습니다.
- `net.core.somaxconn = 65535` (커널 리슨 큐 최대 크기)
- `net.ipv4.tcp_max_syn_backlog = 65535` (SYN 수신 대기 큐 크기)
- 파일 오픈 제한 개방 (`ulimit -n 65535`)

### 2. Docker 컨테이너 네트워크 격리 해제 (sysctls)
Docker Compose 구성에서 격리된 개별 컨테이너 내부 네트워크 스택도 호스트와 동일한 한도를 적용하도록 `docker-compose.server.yml` 파일에 sysctls 옵션을 추가했습니다.
```yaml
sysctls:
  net.core.somaxconn: 65535
  net.ipv4.tcp_max_syn_backlog: 65535
```

### 3. Nginx 리슨 백로그 조정
Nginx 리버스 프록시 진입점의 소켓 대기 한도를 호스트 커널 한도에 맞춰 상향했습니다.
- `listen 80 backlog=65535;`
- `listen 443 ssl backlog=65535;`

### 4. Netty WAS connection-backlog 조정
Netty 기반의 Spring Boot 애플리케이션 리슨 백로그 매개변수를 직접 주입하여 WAS 수준에서의 연결 병목을 예방했습니다.
- `server.netty.connection-backlog: 65535` (api-gateway, queue-service 등)

## 튜닝 결과 및 효과

### 1,000명 동시성
- 튜닝 전 발생하던 36건의 무작위 커넥션 단절(EOF/RST)이 **0.00% 오류율**로 완전히 해결되었습니다.
- 대기열 진입 성공률 100%를 달성했습니다.

### 3,000명 동시성
- 튜닝 전 발생하던 대량의 소켓 연결 거부(status=0) 현상이 크게 완화되어 **7.74% 수준**으로 오류율이 대폭 감소하였습니다.
- 단일 t4g.large 인스턴스의 2 vCPU 컴퓨팅 포화 상태 하에서도 소켓 유실 없이 처리량을 안정적으로 유지하는 성과를 거두었습니다.
