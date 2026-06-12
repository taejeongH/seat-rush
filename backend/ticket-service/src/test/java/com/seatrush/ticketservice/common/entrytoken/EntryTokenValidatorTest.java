package com.seatrush.ticketservice.common.entrytoken;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntryTokenValidatorTest {

    private JwtDecoder jwtDecoder;
    private EntryTokenValidator validator;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        validator = new EntryTokenValidator(jwtDecoder);
    }

    /**
     * 토큰의 사용자와 회차가 요청 정보와 일치하면 검증 결과를 반환합니다.
     */
    @Test
    void validEntryTokenReturnsClaims() {
        when(jwtDecoder.decode("token")).thenReturn(createJwt("10", 20L));

        EntryTokenClaims claims = validator.validate("token", 20L, 10L);

        assertThat(claims.userId()).isEqualTo(10L);
        assertThat(claims.scheduleId()).isEqualTo(20L);
        assertThat(claims.jti()).isEqualTo("entry-token-id");
    }

    /**
     * entryToken의 사용자가 요청 사용자와 다르면 접근을 거부합니다.
     */
    @Test
    void userMismatchIsRejected() {
        when(jwtDecoder.decode("token")).thenReturn(createJwt("10", 20L));

        assertErrorCode(
                () -> validator.validate("token", 20L, 11L),
                ErrorCode.ENTRY_TOKEN_USER_MISMATCH
        );
    }

    /**
     * entryToken의 회차가 요청 회차와 다르면 접근을 거부합니다.
     */
    @Test
    void scheduleMismatchIsRejected() {
        when(jwtDecoder.decode("token")).thenReturn(createJwt("10", 20L));

        assertErrorCode(
                () -> validator.validate("token", 21L, 10L),
                ErrorCode.ENTRY_TOKEN_SCHEDULE_MISMATCH
        );
    }

    /**
     * 서명 또는 만료 검증에 실패한 JWT를 유효하지 않은 entryToken으로 처리합니다.
     */
    @Test
    void invalidJwtIsRejected() {
        when(jwtDecoder.decode("token")).thenThrow(new JwtException("invalid"));

        assertErrorCode(
                () -> validator.validate("token", 20L, 10L),
                ErrorCode.INVALID_ENTRY_TOKEN
        );
    }

    private Jwt createJwt(String subject, Long scheduleId) {
        Instant issuedAt = Instant.now();
        return new Jwt(
                "token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", subject,
                        "scheduleId", scheduleId,
                        "jti", "entry-token-id",
                        "aud", List.of("seat-rush-ticket-service")
                )
        );
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
