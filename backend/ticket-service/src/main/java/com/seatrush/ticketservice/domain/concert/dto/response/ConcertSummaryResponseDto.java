package com.seatrush.ticketservice.domain.concert.dto.response;

import com.seatrush.ticketservice.domain.concert.entity.Concert;

public record ConcertSummaryResponseDto(
        Long concertId,
        String title,
        String venue,
        String posterUrl
) {

    public static ConcertSummaryResponseDto from(Concert concert) {
        return new ConcertSummaryResponseDto(
                concert.getId(),
                concert.getTitle(),
                concert.getVenue(),
                concert.getPosterUrl()
        );
    }
}
