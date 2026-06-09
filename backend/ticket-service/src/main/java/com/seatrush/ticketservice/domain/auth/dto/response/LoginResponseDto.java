package com.seatrush.ticketservice.domain.auth.dto.response;

public record LoginResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static LoginResponseDto of(String accessToken, long expiresIn) {
        return new LoginResponseDto(accessToken, "Bearer", expiresIn);
    }
}
