package com.seatrush.ticketservice.domain.seatlayout.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.dto.response.SeatLayoutSectionResponseDto;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutRepository;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutSeatRepository;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좌석 배치 관련 데이터를 DB 및 Redis 캐시로부터 조회하여 가공하는 비즈니스 서비스입니다.
 */
@Service
@Transactional(readOnly = true)
public class SeatLayoutQueryService {

    private final SeatLayoutRepository layoutRepository;
    private final SeatLayoutSectionRepository sectionRepository;
    private final SeatLayoutSeatRepository seatRepository;
    private final EntryTokenValidator entryTokenValidator;

    public SeatLayoutQueryService(
            SeatLayoutRepository layoutRepository,
            SeatLayoutSectionRepository sectionRepository,
            SeatLayoutSeatRepository seatRepository,
            EntryTokenValidator entryTokenValidator
    ) {
        this.layoutRepository = layoutRepository;
        this.sectionRepository = sectionRepository;
        this.seatRepository = seatRepository;
        this.entryTokenValidator = entryTokenValidator;
    }

    /**
     * 등록된 전체 좌석 배치 템플릿 목록을 반환합니다.
     *
     * @return 좌석 배치 정보 목록 Dto
     */
    public List<SeatLayoutResponseDto> getLayouts() {
        return layoutRepository.findAll().stream()
                .map(SeatLayoutResponseDto::from)
                .toList();
    }

    /**
     * 특정 연습 세션에서 해당 좌석 배치의 구역(Section) 정보들을 조회합니다.
     * 대기열 진입 토큰의 유효성을 검증합니다.
     *
     * @param seatLayoutId 좌석 배치 ID
     * @param practiceSessionId 연습 세션 UUID
     * @param claims 대기열 진입 토큰 클레임
     * @return 구역 목록 Dto
     */
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

    /**
     * 특정 구역에 속한 개별 좌석들의 전체 목록을 조회합니다.
     */
    public List<SeatLayoutSeat> getLayoutSeatsBySection(Long sectionId) {
        return seatRepository.findAllBySectionIdOrderBySortOrderAsc(sectionId);
    }

    /**
     * 구역이 특정 레이아웃 내에 존재하는지 검증합니다.
     */
    public boolean existsSectionInLayout(Long sectionId, Long seatLayoutId) {
        return sectionRepository.existsByIdAndLayoutId(sectionId, seatLayoutId);
    }

    /**
     * 연습 모드 전용 레이아웃 좌석들의 정합성을 검증합니다.
     *
     * @param seatLayoutId 좌석 배치 ID
     * @param seatIds 검증 대상 좌석 ID 목록
     */
    public void validateLayoutSeats(Long seatLayoutId, List<Long> seatIds) {
        List<SeatLayoutSeat> seats = seatRepository.findAllByIdIn(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new CustomException(ErrorCode.SEAT_NOT_FOUND);
        }

        boolean invalidSeat = seats.stream().anyMatch(seat ->
                !sectionRepository.existsByIdAndLayoutId(
                        seat.getSection().getId(),
                        seatLayoutId
                )
        );
        if (invalidSeat) {
            throw new CustomException(ErrorCode.SEAT_NOT_AVAILABLE);
        }
    }

    /**
     * 연습 모드 진입 요청 정보(식별자, 토큰)가 실제와 일치하는지 유효성을 검사합니다.
     */
    public void validatePracticeEntry(
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

