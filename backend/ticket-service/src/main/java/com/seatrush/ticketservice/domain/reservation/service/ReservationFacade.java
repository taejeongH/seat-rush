package com.seatrush.ticketservice.domain.reservation.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.reservation.config.ReservationProperties;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import com.seatrush.ticketservice.domain.reservation.repository.ReservationRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * DB 예매 생성 로직과 Redis 선점 확장 처리를 조합하여 원자적인 흐름을 제어하는 퍼사드(Facade) 서비스입니다.
 * 
 * 1. DB 예매 생성 트랜잭션이 시작되기 전에 먼저 Redis 상의 좌석 선점 소유권을 확인하고 만료 시간(TTL)을 연장합니다.
 * 2. 그 다음 실제 RDB 예매 생성을 트랜잭셔널하게 진행합니다. (짧은 트랜잭션 경계 유지)
 */
@Service
public class ReservationFacade {

    private final ReservationRepository reservationRepository;
    private final SeatHoldService seatHoldService;
    private final ReservationService reservationService;
    private final ReservationProperties properties;

    public ReservationFacade(
            ReservationRepository reservationRepository,
            SeatHoldService seatHoldService,
            ReservationService reservationService,
            ReservationProperties properties
    ) {
        this.reservationRepository = reservationRepository;
        this.seatHoldService = seatHoldService;
        this.reservationService = reservationService;
        this.properties = properties;
    }

    /**
     * 중복 예매 생성 검증 후, Redis의 선점 기한을 결제 대기 기한(Timeout)만큼 선 연장하고,
     * RDB에 예매 레코드를 안전하게 생성합니다.
     *
     * @param holdId 좌석 선점 UUID
     * @param claims 대기열 진입 토큰 정보
     * @return 생성된 예매 결과 Dto
     * @throws CustomException 이미 해당 holdId로 생성된 예매가 존재할 경우
     */
    public ReservationResponseDto create(String holdId, EntryTokenClaims claims) {
        // 이미 생성된 예매인지 선제적 유효성 검사
        if (reservationRepository.existsByHoldId(holdId)) {
            throw new CustomException(ErrorCode.RESERVATION_ALREADY_EXISTS);
        }

        // 1. Redis 선점 기한 연장 처리 (DB 트랜잭션 외부에서 작동)
        Instant expiresAtInstant = Instant.now().plus(properties.paymentTimeout());
        SeatHold hold = seatHoldService.extendForReservation(
                holdId,
                claims,
                properties.paymentTimeout(),
                expiresAtInstant
        );
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                expiresAtInstant,
                ZoneId.systemDefault()
        );

        // 2. RDB 상에 결제 대기 예매 내역 삽입 (DB 트랜잭션 진입)
        return reservationService.create(hold, claims.userId(), expiresAt);
    }
}

