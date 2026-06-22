package com.seatrush.ticketservice.domain.seatlayout.service;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenValidator;
import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayout;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSeat;
import com.seatrush.ticketservice.domain.seatlayout.entity.SeatLayoutSection;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutRepository;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutSeatRepository;
import com.seatrush.ticketservice.domain.seatlayout.repository.SeatLayoutSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 연습 좌석 배치 검증 시 추가 소속 확인 쿼리가 발생하지 않는지 검증합니다.
 */
class SeatLayoutQueryServiceTest {

    private SeatLayoutSectionRepository sectionRepository;
    private SeatLayoutSeatRepository seatRepository;
    private SeatLayoutQueryService service;

    @BeforeEach
    void setUp() {
        SeatLayoutRepository layoutRepository = mock(SeatLayoutRepository.class);
        sectionRepository = mock(SeatLayoutSectionRepository.class);
        seatRepository = mock(SeatLayoutSeatRepository.class);

        service = new SeatLayoutQueryService(
                layoutRepository,
                sectionRepository,
                seatRepository,
                mock(EntryTokenValidator.class)
        );
    }

    /**
     * EntityGraph로 함께 조회한 section.layout 정보만으로 좌석 배치 소속을 검증합니다.
     */
    @Test
    void validateLayoutSeatsUsesFetchedLayoutWithoutAdditionalQuery() {
        SeatLayoutSeat seat = seat(101L, 10L, 1L);
        when(seatRepository.findAllByIdIn(List.of(101L))).thenReturn(List.of(seat));

        Map<Long, Long> result = service.validateLayoutSeats(1L, List.of(101L));

        assertThat(result).containsExactly(Map.entry(101L, 10L));
        verifyNoInteractions(sectionRepository);
    }

    /**
     * 요청한 좌석이 다른 배치에 속하면 메모리에서 비교해 선점을 거부합니다.
     */
    @Test
    void rejectSeatThatBelongsToAnotherLayout() {
        SeatLayoutSeat seat = seat(101L, 10L, 2L);
        when(seatRepository.findAllByIdIn(List.of(101L))).thenReturn(List.of(seat));

        assertThatThrownBy(() -> service.validateLayoutSeats(1L, List.of(101L)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_NOT_AVAILABLE);
    }

    private SeatLayoutSeat seat(Long seatId, Long sectionId, Long layoutId) {
        SeatLayout layout = mock(SeatLayout.class);
        SeatLayoutSection section = mock(SeatLayoutSection.class);
        SeatLayoutSeat seat = mock(SeatLayoutSeat.class);

        when(layout.getId()).thenReturn(layoutId);
        when(section.getId()).thenReturn(sectionId);
        when(section.getLayout()).thenReturn(layout);
        when(seat.getId()).thenReturn(seatId);
        when(seat.getSection()).thenReturn(section);
        return seat;
    }
}