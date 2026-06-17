package com.seatrush.ticketservice.domain.seatlayout.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.seat.repository.SeatHoldRedisRepository;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSeatResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSectionResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutRepository;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutSeatRepository;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SeatLayoutQueryService {

    private final SeatLayoutRepository layoutRepository;
    private final SeatLayoutSectionRepository sectionRepository;
    private final SeatLayoutSeatRepository seatRepository;
    private final SeatHoldRedisRepository holdRedisRepository;
    private final EntryTokenValidator entryTokenValidator;

    public SeatLayoutQueryService(
            SeatLayoutRepository layoutRepository,
            SeatLayoutSectionRepository sectionRepository,
            SeatLayoutSeatRepository seatRepository,
            SeatHoldRedisRepository holdRedisRepository,
            EntryTokenValidator entryTokenValidator
    ) {
        this.layoutRepository = layoutRepository;
        this.sectionRepository = sectionRepository;
        this.seatRepository = seatRepository;
        this.holdRedisRepository = holdRedisRepository;
        this.entryTokenValidator = entryTokenValidator;
    }

    public List<SeatLayoutResponseDto> getLayouts() {
        return layoutRepository.findAll().stream()
                .map(SeatLayoutResponseDto::from)
                .toList();
    }

    public List<SeatLayoutSectionResponseDto> getPracticeSections(
            Long seatLayoutId,
            String practiceSessionId,
            EntryTokenClaims claims
    ) {
        validatePracticeEntry(seatLayoutId, practiceSessionId, claims);
        return sectionRepository.findAllByLayoutIdOrderBySortOrderAsc(seatLayoutId)
                .stream()
                .map(SeatLayoutSectionResponseDto::from)
                .toList();
    }

    public List<SeatLayoutSeatResponseDto> getPracticeSeats(
            Long seatLayoutId,
            Long sectionId,
            String practiceSessionId,
            EntryTokenClaims claims
    ) {
        validatePracticeEntry(seatLayoutId, practiceSessionId, claims);
        if (!sectionRepository.existsByIdAndLayoutId(sectionId, seatLayoutId)) {
            throw new CustomException(ErrorCode.SEAT_SECTION_NOT_FOUND);
        }

        List<SeatLayoutSeat> seats = seatRepository.findAllBySectionIdOrderBySortOrderAsc(sectionId);
        List<Long> seatIds = seats.stream().map(SeatLayoutSeat::getId).toList();
        Map<Long, Boolean> heldSeats = holdRedisRepository.findHeldSeats(
                seatLayoutId,
                seatIds,
                practiceSessionId
        );

        return seats.stream()
                .map(seat -> SeatLayoutSeatResponseDto.from(
                        seat,
                        Boolean.TRUE.equals(heldSeats.get(seat.getId()))
                ))
                .toList();
    }

    private void validatePracticeEntry(
            Long seatLayoutId,
            String practiceSessionId,
            EntryTokenClaims claims
    ) {
        if (!layoutRepository.existsById(seatLayoutId)) {
            throw new CustomException(ErrorCode.SEAT_LAYOUT_NOT_FOUND);
        }
        if (!practiceSessionId.equals(claims.practiceSessionId())) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
        entryTokenValidator.validateSchedule(claims, seatLayoutId);
    }
}
