package com.seatrush.ticketservice.domain.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 예매(Reservation) 프로세스 관련 설정을 관리하는 프로퍼티 클래스입니다.
 * 
 * application.yml 설정 파일에서 `reservation` 접두사(prefix)로 지정된 설정값들과 바인딩됩니다.
 */
@ConfigurationProperties(prefix = "reservation")
public record ReservationProperties(
        /**
         * 사용자가 좌석을 선점(Hold)한 후 결제를 완료해야 하는 제한 시간(Duration)입니다.
         * 이 제한 시간이 지나도록 결제가 완료되지 않으면 해당 예매 건은 만료 대상이 됩니다.
         */
        Duration paymentTimeout,

        /**
         * 스케줄러가 결제 기한이 만료된 예매 건들을 데이터베이스에서 한 번에 조회하여 배치성으로 만료 처리할 때,
         * 1회 작업당 조회하여 처리할 최대 레코드 개수(Batch Size)입니다.
         */
        int expirationBatchSize
) {
}
