package com.seatrush.ticketservice.common.entrytoken;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Queue Service가 발급한 entryToken을 로컬에서 검증합니다.
 */
@Component
public class EntryTokenValidator {

    private final JwtDecoder jwtDecoder;

    public EntryTokenValidator(
            @Qualifier("entryTokenJwtDecoder") JwtDecoder jwtDecoder
    ) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * 서명과 표준 claim을 검증하고 요청 사용자 및 회차와 일치하는지 확인합니다.
     */
    public EntryTokenClaims validate(String entryToken, Long userId) {
        if (entryToken == null || entryToken.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }

        Jwt jwt = decode(entryToken);
        Long tokenUserId = parseLong(jwt.getSubject());
        Number scheduleIdClaim = jwt.getClaim("scheduleId");
        if (scheduleIdClaim == null) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
        Long tokenScheduleId = scheduleIdClaim.longValue();

        if (!tokenUserId.equals(userId)) {
            throw new CustomException(ErrorCode.ENTRY_TOKEN_USER_MISMATCH);
        }

        return new EntryTokenClaims(
                jwt.getId(),
                tokenUserId,
                tokenScheduleId,
                jwt.getExpiresAt()
        );
    }

    /**
     * 검증된 entryToken의 회차와 요청 대상 회차가 일치하는지 확인합니다.
     */
    public void validateSchedule(EntryTokenClaims claims, Long scheduleId) {
        if (!claims.scheduleId().equals(scheduleId)) {
            throw new CustomException(ErrorCode.ENTRY_TOKEN_SCHEDULE_MISMATCH);
        }
    }

    private Jwt decode(String entryToken) {
        try {
            return jwtDecoder.decode(entryToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
    }
}
