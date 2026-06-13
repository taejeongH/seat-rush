package com.seatrush.ticketservice.domain.seat.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.seat.config.SeatHoldProperties;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatHoldResponseDto;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldRedisRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldResult;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * 좌석 선점 생성, 조회, 해제를 처리합니다.
 */
@Service
@Transactional(readOnly = true)
public class SeatHoldService {

    private final SeatRepository seatRepository;
    private final SeatHoldRedisRepository holdRedisRepository;
    private final EntryTokenValidator entryTokenValidator;
    private final SeatHoldProperties properties;

    public SeatHoldService(
            SeatRepository seatRepository,
            SeatHoldRedisRepository holdRedisRepository,
            EntryTokenValidator entryTokenValidator,
            SeatHoldProperties properties
    ) {
        this.seatRepository = seatRepository;
        this.holdRedisRepository = holdRedisRepository;
        this.entryTokenValidator = entryTokenValidator;
        this.properties = properties;
    }

    /**
     * 요청한 좌석 전체가 선점 가능할 때만 하나의 hold로 원자적으로 선점합니다.
     */
    public SeatHoldResponseDto hold(
            Long scheduleId,
            EntryTokenClaims claims,
            List<Long> requestedSeatIds
    ) {
        entryTokenValidator.validateSchedule(claims, scheduleId);
        List<Long> seatIds = validateSeatIds(requestedSeatIds);
        validateSeats(scheduleId, seatIds);

        Instant expiresAt = Instant.now().plus(properties.ttl());
        SeatHold hold = new SeatHold(
                UUID.randomUUID().toString(),
                scheduleId,
                claims.userId(),
                claims.jti(),
                seatIds,
                expiresAt
        );
        SeatHoldResult result = holdRedisRepository.hold(
                hold,
                properties.ttl().toMillis()
        );

        if (!result.success()) {
            throw new CustomException(ErrorCode.SEAT_NOT_AVAILABLE);
        }
        return SeatHoldResponseDto.from(hold);
    }

    /**
     * hold 소유자와 entryToken을 확인한 뒤 유효한 선점 정보를 조회합니다.
     */
    public SeatHoldResponseDto get(String holdId, EntryTokenClaims claims) {
        SeatHold hold = findHold(holdId);
        validateHoldAccess(hold, claims);
        return SeatHoldResponseDto.from(hold);
    }

    /**
     * hold 소유자와 entryToken을 확인한 뒤 해당 hold의 좌석을 즉시 해제합니다.
     */
    public SeatHoldResponseDto release(String holdId, EntryTokenClaims claims) {
        SeatHold hold = findHold(holdId);
        validateHoldAccess(hold, claims);

        if (!holdRedisRepository.release(hold)) {
            throw new CustomException(ErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        return SeatHoldResponseDto.from(hold);
    }

    private List<Long> validateSeatIds(List<Long> requestedSeatIds) {
        if (requestedSeatIds == null
                || requestedSeatIds.isEmpty()
                || requestedSeatIds.size() > properties.maxSeats()
                || new HashSet<>(requestedSeatIds).size() != requestedSeatIds.size()) {
            if (requestedSeatIds != null && requestedSeatIds.size() > properties.maxSeats()) {
                throw new CustomException(ErrorCode.SEAT_HOLD_LIMIT_EXCEEDED);
            }
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return requestedSeatIds.stream().sorted().toList();
    }

    private void validateSeats(Long scheduleId, List<Long> seatIds) {
        List<Seat> seats = seatRepository.findAllByIdIn(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new CustomException(ErrorCode.SEAT_NOT_FOUND);
        }

        boolean invalidSeat = seats.stream().anyMatch(seat ->
                !seat.getSection().getSchedule().getId().equals(scheduleId)
                        || seat.getStatus() != SeatStatus.AVAILABLE
        );
        if (invalidSeat) {
            throw new CustomException(ErrorCode.SEAT_NOT_AVAILABLE);
        }
    }

    private SeatHold findHold(String holdId) {
        SeatHold hold = holdRedisRepository.findById(holdId);
        if (hold == null) {
            throw new CustomException(ErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        return hold;
    }

    private void validateHoldAccess(SeatHold hold, EntryTokenClaims claims) {
        entryTokenValidator.validateSchedule(claims, hold.scheduleId());
        if (!hold.userId().equals(claims.userId())
                || !hold.entryTokenId().equals(claims.jti())) {
            throw new CustomException(ErrorCode.SEAT_HOLD_ACCESS_DENIED);
        }
    }
}
