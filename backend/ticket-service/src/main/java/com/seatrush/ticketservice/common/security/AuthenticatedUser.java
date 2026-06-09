package com.seatrush.ticketservice.common.security;

import com.seatrush.ticketservice.domain.auth.entity.UserRole;

/**
 * JWT 검증 후 SecurityContext에 저장되는 인증 사용자 정보입니다.
 * Controller에서 {@code @AuthenticationPrincipal}로 현재 사용자를 조회할 수 있습니다.
 */
public record AuthenticatedUser(
        Long userId,
        String email,
        UserRole role
) {
}
