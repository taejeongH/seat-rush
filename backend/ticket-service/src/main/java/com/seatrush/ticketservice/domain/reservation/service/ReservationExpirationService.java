package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.domain.reservation.config.ReservationProperties;
import com.seatrush.ticketservice.domain.reservation.entity.Reservation;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 기한이 지난 예매를 배치 단위로 만료 처리합니다.
 */
@Service
public class ReservationExpirationService {

    private final ReservationRepository reservationRepository;
    private final ReservationHoldReleaseService holdReleaseService;
    private final ReservationProperties properties;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            ReservationHoldReleaseService holdReleaseService,
            ReservationProperties properties
    ) {
        this.reservationRepository = reservationRepository;
        this.holdReleaseService = holdReleaseService;
        this.properties = properties;
    }

    /**
     * 만료 대상 예매를 잠금 획득한 배치만큼 처리하고 처리 건수를 반환합니다.
     */
    @Transactional
    public int expireBatch(LocalDateTime now) {
        List<Reservation> reservations = reservationRepository.findExpirableReservations(
                now,
                properties.expirationBatchSize()
        );
        reservations.forEach(reservation -> {
            reservation.expire(now);
            holdReleaseService.releaseAfterCommit(reservation.getHoldId());
        });
        return reservations.size();
    }
}
