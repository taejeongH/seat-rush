package com.seatrush.ticketservice.domain.seat.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.metrics.BusinessMetrics;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.seat.config.SeatHoldProperties;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatHoldResponseDto;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.entity.SeatStatus;
import com.seatrush.ticketservice.domain.seat.repository.SeatHold;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldRedisRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldResult;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import com.seatrush.ticketservice.domain.seatlayout.service.SeatLayoutQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 실시간 좌석 선점(Hold) 라이프사이클을 관리하는 핵심 비즈니스 서비스입니다.
 * 
 * Redis를 백엔드로 활용하여 여러 사용자가 동시에 동일한 좌석을 잡지 못하도록 원자적(Atomic)으로 락을 관리하고,
 * 선점 만료(TTL) 설정 및 연장, 예매 취소 시 즉시 해제 처리를 지원합니다.
 */
@Service
@Transactional(readOnly = true)
public class SeatHoldService {

    private final SeatRepository seatRepository;
    private final SeatLayoutQueryService layoutQueryService;
    private final SeatHoldRedisRepository holdRedisRepository;
    private final EntryTokenValidator entryTokenValidator;
    private final SeatHoldProperties properties;
    private final BusinessMetrics businessMetrics;

    public SeatHoldService(
            SeatRepository seatRepository,
            SeatLayoutQueryService layoutQueryService,
            SeatHoldRedisRepository holdRedisRepository,
            EntryTokenValidator entryTokenValidator,
            SeatHoldProperties properties,
            BusinessMetrics businessMetrics
    ) {
        this.seatRepository = seatRepository;
        this.layoutQueryService = layoutQueryService;
        this.holdRedisRepository = holdRedisRepository;
        this.entryTokenValidator = entryTokenValidator;
        this.properties = properties;
        this.businessMetrics = businessMetrics;
    }

    /**
     * 사용자가 선택한 일련의 좌석(seatIds)들을 원자적으로 선점합니다.
     * 
     * 1. 대기열 토큰의 공연 회차가 타겟 회차와 일치하는지 확인합니다.
     * 2. 요청한 모든 좌석이 해당 회차에 존재하고 현재 예약 가능 상태(AVAILABLE)인지 1차 확인합니다.
     * 3. Redis 분산 락/선점 상태 처리를 통해 최종 선점을 확정합니다. (한 개라도 이미 선점되어 있다면 전체 실패)
     *
     * @param scheduleId 공연 회차 ID (혹은 연습모드일 경우 좌석배치 ID)
     * @param claims 대기열 진입 토큰 정보
     * @param requestedSeatIds 선점할 좌석 식별자 목록
     * @return 성공 시 선점 ID와 만료시각 정보 Dto
     * @throws CustomException 이미 선점되었거나 좌석이 유효하지 않을 경우
     */
    public SeatHoldResponseDto hold(
            Long scheduleId,
            EntryTokenClaims claims,
            List<Long> requestedSeatIds
    ) {
        return businessMetrics.record("seat.hold", mode(claims), () -> {
            entryTokenValidator.validateSchedule(claims, scheduleId);
            List<Long> seatIds = validateSeatIds(requestedSeatIds);

            if (claims.practiceMode()) {
                validateLayoutSeats(scheduleId, seatIds);
            } else {
                validateSeats(scheduleId, seatIds);
            }

            Instant expiresAt = Instant.now().plus(properties.ttl());
            SeatHold hold = new SeatHold(
                    UUID.randomUUID().toString(),
                    scheduleId,
                    claims.userId(),
                    claims.jti(),
                    claims.practiceSessionId(),
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
        });
    }

    /**
     * 특정 선점(holdId) 정보를 확인하고 소유권 여부를 체크하여 안전하게 반환합니다.
     *
     * @param holdId 좌석 선점 UUID
     * @param claims 요청자의 대기열 토큰
     * @return 선점 상태 Dto
     */
    public SeatHoldResponseDto get(String holdId, EntryTokenClaims claims) {
        SeatHold hold = findHold(holdId, claims.practiceSessionId());
        validateHoldAccess(hold, claims);
        return SeatHoldResponseDto.from(hold);
    }

    /**
     * 사용자가 명시적으로 선택 해제 혹은 취소를 요청한 경우 해당 선점을 즉시 해제합니다.
     *
     * @param holdId 좌석 선점 UUID
     * @param claims 요청자의 대기열 토큰
     * @return 해제된 선점 상태 Dto
     */
    public SeatHoldResponseDto release(String holdId, EntryTokenClaims claims) {
        return businessMetrics.record("seat.hold.release", mode(claims), () -> {
            SeatHold hold = findHold(holdId, claims.practiceSessionId());
            validateHoldAccess(hold, claims);

            if (!holdRedisRepository.release(hold)) {
                throw new CustomException(ErrorCode.SEAT_HOLD_NOT_FOUND);
            }
            return SeatHoldResponseDto.from(hold);
        });
    }

    /**
     * 예매 생성 API 진입 직전에 선점 소유권을 재검증하고,
     * 예매 만료(결제 대기시간 등) 시점까지 Redis 선점 유효시간(TTL)을 일시적으로 연장합니다.
     * 
     * DB 트랜잭션과 독립되게 연장 처리하도록 트랜잭션 전파 옵션을 비활성화(NOT_SUPPORTED)합니다.
     *
     * @param holdId 좌석 선점 UUID
     * @param claims 요청자의 대기열 토큰
     * @param ttl 연장할 유효시간 (결제 대기 만료시간 등)
     * @param expiresAt 연장 만료 시점 Instant
     * @return 연장된 선점 객체
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SeatHold extendForReservation(
            String holdId,
            EntryTokenClaims claims,
            Duration ttl,
            Instant expiresAt
    ) {
        SeatHold hold = findHold(holdId, claims.practiceSessionId());
        validateHoldAccess(hold, claims);

        if (!holdRedisRepository.extendForReservation(
                hold,
                ttl.toMillis(),
                expiresAt
        )) {
            throw new CustomException(ErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        return hold;
    }

    /**
     * 비동기 결제 처리 도중 실패하거나 만료되어 예매가 취소된 경우,
     * 해당 holdId를 식별하여 선점 상태가 남아 있다면 강제로 즉시 해제 처리합니다.
     *
     * @param holdId 좌석 선점 UUID
     */
    public void releaseIfPresent(String holdId) {
        SeatHold hold = holdRedisRepository.findById(holdId);
        if (hold != null) {
            holdRedisRepository.release(hold);
        }
    }

    /**
     * 지정된 좌석 ID 목록들에 대해 Redis 선점 상태를 일괄 조회하여 맵 형태로 반환합니다.
     *
     * @param scheduleId 공연 회차 ID
     * @param seatIds 조회 대상 좌석 ID 목록
     * @param practiceSessionId 활성화된 연습 세션 식별 ID
     * @return 각 좌석 ID별 실시간 선점 여부 (True: 선점됨, False: 미선점)
     */
    public Map<Long, Boolean> findHeldSeats(
            Long scheduleId,
            List<Long> seatIds,
            String practiceSessionId
    ) {
        return holdRedisRepository.findHeldSeats(scheduleId, seatIds, practiceSessionId);
    }

    /**
     * 요청된 좌석 ID 리스트의 포맷 및 크기 한계(최대 선택 가능 개수)를 검증합니다.
     */
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

    /**
     * 실제 공연 회차의 좌석들이 모두 사용 가능한지 DB 상에서 검증합니다.
     */
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
        return findHold(holdId, null);
    }

    /**
     * 연습 모드 전용 레이아웃 좌석들의 정합성을 검증합니다.
     */
    private void validateLayoutSeats(Long seatLayoutId, List<Long> seatIds) {
        layoutQueryService.validateLayoutSeats(seatLayoutId, seatIds);
    }

    /**
     * Redis 캐시로부터 선점 상세 정보를 찾아 반환하며, 존재하지 않을 시 예외를 발생시킵니다.
     */
    private SeatHold findHold(String holdId, String practiceSessionId) {
        SeatHold hold = holdRedisRepository.findById(holdId, practiceSessionId);
        if (hold == null) {
            throw new CustomException(ErrorCode.SEAT_HOLD_NOT_FOUND);
        }
        return hold;
    }

    /**
     * 현재 접근하는 사용자의 토큰 정보와 대상 선점 정보의 소유주가 일치하는지 접근 보안을 검증합니다.
     */
    private void validateHoldAccess(SeatHold hold, EntryTokenClaims claims) {
        entryTokenValidator.validateSchedule(claims, hold.scheduleId());
        if (!hold.userId().equals(claims.userId())
                || !hold.entryTokenId().equals(claims.jti())
                || !samePracticeSession(hold.practiceSessionId(), claims.practiceSessionId())) {
            throw new CustomException(ErrorCode.SEAT_HOLD_ACCESS_DENIED);
        }
    }

    private boolean samePracticeSession(String holdPracticeSessionId, String tokenPracticeSessionId) {
        if (holdPracticeSessionId == null || holdPracticeSessionId.isBlank()) {
            return tokenPracticeSessionId == null || tokenPracticeSessionId.isBlank();
        }
        return holdPracticeSessionId.equals(tokenPracticeSessionId);
    }
    private String mode(EntryTokenClaims claims) {
        if (claims == null || !claims.practiceMode()) {
            return "real";
        }
        return "practice";
    }

}

