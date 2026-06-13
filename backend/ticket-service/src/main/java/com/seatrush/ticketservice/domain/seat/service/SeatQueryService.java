package com.seatrush.ticketservice.domain.seat.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.concert.repository.ConcertScheduleRepository;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatResponseDto;
import com.seatrush.ticketservice.domain.seat.dto.response.SeatSectionResponseDto;
import com.seatrush.ticketservice.domain.seat.entity.Seat;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldRedisRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatRepository;
import com.seatrush.ticketservice.domain.seat.repository.SeatSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 회차별 좌석 구역과 실시간 좌석 상태를 조회합니다.
 */
@Service
@Transactional(readOnly = true)
public class SeatQueryService {

    private final ConcertScheduleRepository scheduleRepository;
    private final SeatSectionRepository sectionRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldRedisRepository holdRedisRepository;
    private final EntryTokenValidator entryTokenValidator;

    public SeatQueryService(
            ConcertScheduleRepository scheduleRepository,
            SeatSectionRepository sectionRepository,
            SeatRepository seatRepository,
            SeatHoldRedisRepository holdRedisRepository,
            EntryTokenValidator entryTokenValidator
    ) {
        this.scheduleRepository = scheduleRepository;
        this.sectionRepository = sectionRepository;
        this.seatRepository = seatRepository;
        this.holdRedisRepository = holdRedisRepository;
        this.entryTokenValidator = entryTokenValidator;
    }

    /**
     * 입장 권한을 확인하고 회차에 속한 좌석 구역을 표시 순서대로 조회합니다.
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
     * DB 좌석 상태와 Redis 선점 상태를 합쳐 구역별 좌석 상태를 조회합니다.
     */
    public List<SeatResponseDto> getSeats(
            Long scheduleId,
            Long sectionId,
            EntryTokenClaims claims
    ) {
        validateEntry(scheduleId, claims);
        if (!sectionRepository.existsByIdAndScheduleId(sectionId, scheduleId)) {
            throw new CustomException(ErrorCode.SEAT_SECTION_NOT_FOUND);
        }

        List<Seat> seats = seatRepository.findAllBySectionIdOrderByRowNameAscSeatNumberAsc(sectionId);
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();
        Map<Long, Boolean> heldSeats = holdRedisRepository.findHeldSeats(scheduleId, seatIds);

        return seats.stream()
                .map(seat -> SeatResponseDto.from(
                        seat,
                        Boolean.TRUE.equals(heldSeats.get(seat.getId()))
                ))
                .toList();
    }

    private void validateEntry(Long scheduleId, EntryTokenClaims claims) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new CustomException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        entryTokenValidator.validateSchedule(claims, scheduleId);
    }
}
