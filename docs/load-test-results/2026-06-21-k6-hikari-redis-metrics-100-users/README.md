# HikariCP 및 Redis hold 세부 계측 - 사용자 100명

## 목적

좌석 조회 지연이 DB 커넥션 풀, Redis 서버, Lettuce 클라이언트, hold 키 처리 중 어디에서 발생하는지 분리해 확인합니다.

## 환경과 절차

- 대상: 배포 EC2 `t4g.large`, API Gateway 경유 HTTPS 요청
- 시나리오: 연습 세션 생성부터 대기열, 좌석 조회·선점, 예매, 결제 완료까지
- 좌석 조회: 섹션당 2,500석, 전체 10,000석
- Prometheus: Spring 애플리케이션 scrape 주기 `1초`
- 사전 부하: 실제 측정에서 제외한 100명 전체 흐름 1회
- 안정화: 사전 부하 완료 후 60초 대기
- 본 측정: 100명 동시 사용자, 실행 간 30초 수집 창 분리

## 핵심 결과

| 실행 | 예매 확정 | HTTP 실패율 | 전체 HTTP p95 (ms) | 좌석 조회 p90 (ms) | 좌석 조회 p95 (ms) | 좌석 조회 p99 (ms) | 처리량 (req/s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 100 / 100 | 0.000% | 2,351 | 2,484 | 2,641 | 3,570 | 42.72 |
| 2 | 100 / 100 | 0.000% | 1,967 | 1,872 | 2,016 | 2,253 | 45.43 |
| 3 | 100 / 100 | 0.000% | 1,530 | 1,832 | 1,974 | 2,237 | 54.40 |
| 중앙값 | 100 / 100 | 0.000% | 1,967 | 1,872 | 2,016 | 2,253 | 45.43 |

## 좌석 조회 내부 구간 p95

| 구간 | 실행 1 | 실행 2 | 실행 3 | 중앙값 |
| --- | ---: | ---: | ---: | ---: |
| Repository | 126 ms | 190 ms | 104 ms | 126 ms |
| hold 조회 전체 | 525 ms | 261 ms | 356 ms | 356 ms |
| hold 키 2,500개 생성 | 77 ms | 67 ms | 111 ms | 77 ms |
| Redis `MGET` 호출 | 447 ms | 246 ms | 289 ms | 289 ms |
| hold 결과 매핑 | 3 ms | 20 ms | 10 ms | 10 ms |

## DB 커넥션 풀과 Redis MGET

| 지표 | 중앙값 | 해석 |
| --- | ---: | --- |
| HikariCP active 최대 | 10 | 풀 최대치 10개가 모두 사용됨 |
| HikariCP pending 최대 | 75 | DB 커넥션 획득 대기 발생 |
| HikariCP acquire 평균 | 460 ms | 전체 예매 흐름에서 커넥션 획득 대기 비중이 큼 |
| HikariCP timeout | 0 | 대기는 발생했지만 timeout까지는 도달하지 않음 |
| Lettuce `MGET` 평균 | 65 ms | Ticket Service 클라이언트 측 Redis 호출 시간 |
| Lettuce `MGET` 최대 | 436 ms | 순간 부하에서 클라이언트 대기 발생 |
| Seat Redis `MGET` 평균 | 0.38 ms | Redis 서버 명령 처리 자체는 빠름 |

## 해석

- JPA projection 적용 후 Repository p95는 `126ms`로 유지돼, 좌석 엔티티 전체 로딩이 주 병목은 아닙니다.
- Seat Redis 서버의 `MGET`은 평균 `0.38ms`로 매우 빠르지만, Ticket Service의 `MGET` 호출 p95는 `289ms`입니다.
  Redis 서버 처리보다 2,500개 키 생성, Lettuce 클라이언트 대기, 네트워크·응답 경로 비용이 더 큰 비중을 차지합니다.
- HikariCP는 active 최대 10, pending 최대 75가 관찰됐습니다. 다만 이 지표는 좌석 조회뿐 아니라 예매 생성 등 전체 흐름의 DB 작업을 함께 포함하므로,
  좌석 조회 Repository만의 문제라고 단정하지 않습니다.

## 다음 개선 후보

1. 구역별 선점 좌석 인덱스를 도입해 2,500개 hold key 생성과 `MGET` 조회 범위를 줄입니다.
2. 예매 생성·결제 결과 처리 등 DB 사용 경로별 커넥션 사용량을 분리 계측해 HikariCP 대기의 실제 원인을 좁힙니다.

## 원본 결과

- 사전 부하: [preheat](./preheat/summary.json)
- 본 측정: [실행 1](./runs/run-1/summary.json), [실행 2](./runs/run-2/summary.json), [실행 3](./runs/run-3/summary.json)
