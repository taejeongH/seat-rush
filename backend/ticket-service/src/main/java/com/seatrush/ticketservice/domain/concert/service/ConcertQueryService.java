package com.seatrush.ticketservice.domain.concert.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.PageResponseDto;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertDetailResponseDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertScheduleResponseDto;
import com.seatrush.ticketservice.domain.concert.dto.response.ConcertSummaryResponseDto;
import com.seatrush.ticketservice.domain.concert.entity.Concert;
import com.seatrush.ticketservice.domain.concert.repository.ConcertRepository;
import com.seatrush.ticketservice.domain.concert.repository.ConcertScheduleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공연과 회차 정보를 조회하는 서비스입니다.
 */
@Service
@Transactional(readOnly = true)
public class ConcertQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ConcertRepository concertRepository;
    private final ConcertScheduleRepository concertScheduleRepository;

    public ConcertQueryService(
            ConcertRepository concertRepository,
            ConcertScheduleRepository concertScheduleRepository
    ) {
        this.concertRepository = concertRepository;
        this.concertScheduleRepository = concertScheduleRepository;
    }

    /**
     * 공연 목록을 최신 등록순으로 페이지 조회합니다.
     */
    public PageResponseDto<ConcertSummaryResponseDto> getConcerts(int page, int size) {
        validatePageRequest(page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<ConcertSummaryResponseDto> concerts = concertRepository.findAll(pageRequest)
                .map(ConcertSummaryResponseDto::from);

        return PageResponseDto.from(concerts);
    }

    /**
     * 공연 ID에 해당하는 상세 정보를 조회합니다.
     */
    public ConcertDetailResponseDto getConcert(Long concertId) {
        return ConcertDetailResponseDto.from(findConcert(concertId));
    }

    /**
     * 공연에 등록된 회차를 공연 일시순으로 조회합니다.
     */
    public List<ConcertScheduleResponseDto> getConcertSchedules(Long concertId) {
        findConcert(concertId);

        return concertScheduleRepository.findAllByConcertIdOrderByPerformanceAtAsc(concertId)
                .stream()
                .map(ConcertScheduleResponseDto::from)
                .toList();
    }

    /**
     * 공연 회차 ID의 존재 여부를 검증하고 존재하지 않으면 예외를 발생시킵니다.
     */
    public void validateScheduleExists(Long scheduleId) {
        if (!concertScheduleRepository.existsById(scheduleId)) {
            throw new CustomException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
    }

    /**
     * 공연을 조회하고 존재하지 않으면 공연 조회 예외를 발생시킵니다.
     */
    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONCERT_NOT_FOUND));
    }

    /**
     * 페이지 번호와 페이지 크기가 허용 범위인지 검증합니다.
     */
    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
