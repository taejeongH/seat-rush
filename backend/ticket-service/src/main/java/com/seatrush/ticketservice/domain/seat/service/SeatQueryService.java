package com.seatrush.ticketservice.domain.seat.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.metrics.BusinessMetrics;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.concert.service.ConcertQueryService;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatResponseDto;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatSectionResponseDto;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.service.SeatHoldService;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatSectionRepository;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSeatResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;
import com.seatrush.ticketservice.domain.seatlayout.service.SeatLayoutQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 공연 회차(Schedule)별 구역 및 실시간 좌석 상태 조회를 처리하는 서비스 컴포넌트입니다.
 * 
 * 데이터베이스의 정적 좌석 배치/등급 정보와 Redis의 동적 선점(Hold) 상태 데이터를 취합하여
 * 사용자에게 최신의 실시간 좌석 가용 상태를 전달합니다.
 */
@Service
@Transactional(readOnly = true)
public class SeatQueryService {

    private final ConcertQueryService concertQueryService;
    private final SeatSectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldService seatHoldService;
    private final SeatLayoutQueryService layoutQueryService;
    private final EntryTokenValidator entryTokenValidator;
    private final BusinessMetrics businessMetrics;

    public SeatQueryService(
            ConcertQueryService concertQueryService,
            SeatSectionRepository sectionRepository,
            SeatRepository seatRepository,
            SeatHoldService seatHoldService,
            SeatLayoutQueryService layoutQueryService,
            EntryTokenValidator entryTokenValidator,
            BusinessMetrics businessMetrics
    ) {
        this.concertQueryService = concertQueryService;
        this.sectionRepository = sectionRepository;
        this.seatRepository = seatRepository;
        this.seatHoldService = seatHoldService;
        this.layoutQueryService = layoutQueryService;
        this.entryTokenValidator = entryTokenValidator;
        this.businessMetrics = businessMetrics;
    }

    /**
     * 특정 공연 회차에 설정되어 있는 모든 좌석 구역(Section) 정보 리스트를 반환합니다.
     *
     * @param scheduleId 공연 회차 ID
     * @param claims 대기열 진입 토큰 정보
     * @return 구역 목록 Dto
     */
    public List<SeatSectionResponseDto> getSections(
            Long scheduleId,
            EntryTokenClaims claims
    ) {
        validateEntry(scheduleId, claims);

        return sectionRepository.findAllByScheduleIdOrderBySortOrderAsc(scheduleId)
                .stream()
                .map(SeatSectionResponseDto::from)
                .toList();
    }

    /**
     * 특정 구역(Section)의 모든 개별 좌석 상태 정보를 조회합니다.
     * DB의 기본 좌석 테이블 데이터에 Redis에 실시간 캐시된 선점 여부(Hold)를 오버레이하여 반환합니다.
     *
     * @param scheduleId 공연 회차 ID
     * @param sectionId 좌석 구역 ID
     * @param claims 대기열 진입 토큰 정보
     * @return 실시간 상태를 포함한 좌석 목록 Dto
     */
    public List<SeatResponseDto> getSeats(
            Long scheduleId,
            Long sectionId,
            EntryTokenClaims claims
    ) {
        return businessMetrics.record("seat.query", "real", () -> {
            validateTokenSchedule(scheduleId, claims);

            List<Seat> seats = businessMetrics.record(
                    "seat.query.repository",
                    "real",
                    () -> seatRepository.findAllBySectionIdAndSectionScheduleIdOrderByRowNameAscSeatNumberAsc(
                            sectionId,
                            scheduleId
                    )
            );
            if (seats.isEmpty()) {
                throw new CustomException(ErrorCode.SEAT_SECTION_NOT_FOUND);
            }

            List<Long> seatIds = seats.stream().map(Seat::getId).toList();
            Map<Long, Boolean> heldSeats = businessMetrics.record(
                    "seat.query.hold.read",
                    "real",
                    () -> seatHoldService.findHeldSeats(scheduleId, seatIds, null)
            );

            return businessMetrics.record(
                    "seat.query.mapping",
                    "real",
                    () -> seats.stream()
                            .map(seat -> SeatResponseDto.from(
                                    seat,
                                    Boolean.TRUE.equals(heldSeats.get(seat.getId()))
                            ))
                            .toList()
            );
        });
    }

    /**
     * 요청 회차가 존재하는지 확인하고, 해당 회차로 발급된 토큰인지 대기열 토큰 매칭 여부를 검증합니다.
     */
    private void validateEntry(Long scheduleId, EntryTokenClaims claims) {
        concertQueryService.validateScheduleExists(scheduleId);
        validateTokenSchedule(scheduleId, claims);
    }

    /**
     * 대기열 진입 토큰이 요청한 회차에 대해 발급되었는지 검증합니다.
     */
    private void validateTokenSchedule(Long scheduleId, EntryTokenClaims claims) {
        entryTokenValidator.validateSchedule(claims, scheduleId);
    }

    /**
     * 특정 연습 세션과 구역에 해당하는 개별 좌석들의 목록을 반환하며,
     * 각 좌석이 Redis 상에서 임시 선점(Hold) 상태인지 여부를 판별하여 응답 Dto에 매핑합니다.
     */
    public List<SeatLayoutSeatResponseDto> getPracticeSeats(
            Long seatLayoutId,
            Long sectionId,
            String practiceSessionId,
            EntryTokenClaims claims
    ) {
        return businessMetrics.record("seat.query", "practice", () -> {
            layoutQueryService.validatePracticeToken(seatLayoutId, practiceSessionId, claims);

            List<SeatLayoutSeat> seats = businessMetrics.record(
                    "seat.query.repository",
                    "practice",
                    () -> layoutQueryService.getLayoutSeats(sectionId, seatLayoutId)
            );
            if (seats.isEmpty()) {
                throw new CustomException(ErrorCode.SEAT_SECTION_NOT_FOUND);
            }

            List<Long> seatIds = seats.stream().map(SeatLayoutSeat::getId).toList();
            Map<Long, Boolean> heldSeats = businessMetrics.record(
                    "seat.query.hold.read",
                    "practice",
                    () -> seatHoldService.findHeldSeats(seatLayoutId, seatIds, practiceSessionId)
            );

            return businessMetrics.record(
                    "seat.query.mapping",
                    "practice",
                    () -> seats.stream()
                            .map(seat -> SeatLayoutSeatResponseDto.from(
                                    seat,
                                    Boolean.TRUE.equals(heldSeats.get(seat.getId()))
                            ))
                            .toList()
            );
        });
    }
}

