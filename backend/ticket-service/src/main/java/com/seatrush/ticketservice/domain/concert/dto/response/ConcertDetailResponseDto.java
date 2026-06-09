package com.seatrush.ticketservice.domain.concert.dto.response;

import com.seatrush.ticketservice.domain.concert.entity.Concert;

public record ConcertDetailResponseDto(
        Long concertId,
        String title,
        String description,
        String venue,
        String posterUrl
) {

    public static ConcertDetailResponseDto from(Concert concert) {
        return new ConcertDetailResponseDto(
                concert.getId(),
                concert.getTitle(),
                concert.getDescription(),
                concert.getVenue(),
                concert.getPosterUrl()
        );
    }
}
