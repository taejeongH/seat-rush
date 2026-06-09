package com.seatrush.ticketservice.domain.auth.dto.response;

import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.auth.entity.UserRole;

public record UserResponseDto(
        Long userId,
        String email,
        String name,
        UserRole role
) {

    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
    }
}
