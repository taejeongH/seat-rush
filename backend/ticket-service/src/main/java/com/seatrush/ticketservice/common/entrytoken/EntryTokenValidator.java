package com.seatrush.ticketservice.common.entrytoken;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * 대기열 서비스(Queue Service)에서 발행해준 진입 토큰(JWT)의 서명을 확인하고,
 * 토큰 정보(UserId, ScheduleId)와 요청 사용자의 상태가 매칭되는지 세부 검증하는 컴포넌트입니다.
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
     * JWT 토큰 서명을 검증하고 내부에 포함된 만료일, 사용자 식별자 및 대상 회차(ScheduleId) 정보를 대조합니다.
     *
     * @param entryToken 검증할 대기열 진입 토큰 JWT 문자열
     * @param userId 요청을 보낸 실제 사용자의 ID
     * @return 검증 완료 후 추출한 대기열 진입 클레임 객체
     * @throws CustomException 토큰이 유효하지 않거나 요청 사용자와 일치하지 않는 경우
     */
    public EntryTokenClaims validate(String entryToken, Long userId) {
        if (entryToken == null || entryToken.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }

        Jwt jwt = decode(entryToken);
        Long tokenUserId = parseLong(jwt.getSubject());
        
        // 회차 식별자 검증
        Number scheduleIdClaim = jwt.getClaim("scheduleId");
        if (scheduleIdClaim == null) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
        Long tokenScheduleId = scheduleIdClaim.longValue();

        // 토큰의 대상 사용자와 실제 요청한 사용자가 일치하는지 검증 (사용자 가로채기 방지)
        if (!tokenUserId.equals(userId)) {
            throw new CustomException(ErrorCode.ENTRY_TOKEN_USER_MISMATCH);
        }

        return new EntryTokenClaims(
                jwt.getId(),
                tokenUserId,
                tokenScheduleId,
                jwt.getClaimAsString("practiceSessionId"),
                jwt.getExpiresAt()
        );
    }

    /**
     * 검증 완료된 대기열 진입 정보와 실제 사용자가 예약하려고 시도하는 대상 공연 회차가 일치하는지 추가 검증합니다.
     *
     * @param claims 검증된 토큰 클레임 세트
     * @param scheduleId 사용자가 예약을 진행하고자 하는 대상 공연 회차 ID
     * @throws CustomException 진입 토큰의 회차 정보와 타겟 회차가 불일치할 경우
     */
    public void validateSchedule(EntryTokenClaims claims, Long scheduleId) {
        if (!claims.scheduleId().equals(scheduleId)) {
            throw new CustomException(ErrorCode.ENTRY_TOKEN_SCHEDULE_MISMATCH);
        }
    }

    /**
     * 대기열 진입 토큰(JWT) 문자열을 디코딩합니다.
     */
    private Jwt decode(String entryToken) {
        try {
            return jwtDecoder.decode(entryToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
    }

    /**
     * 문자열 숫자를 안전하게 파싱합니다.
     */
    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_TOKEN);
        }
    }
}

