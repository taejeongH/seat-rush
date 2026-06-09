package com.seatrush.ticketservice.common.security;

import com.seatrush.ticketservice.common.response.status.ErrorCode;
import org.springframework.security.core.AuthenticationException;

/**
 * Security 필터에서 발생한 인증 실패 사유를 ErrorCode와 함께 전달하는 예외입니다.
 * JwtAuthenticationEntryPoint가 이 예외를 표준 API 에러 응답으로 변환합니다.
 */
public class CustomAuthenticationException extends AuthenticationException {

    private final ErrorCode errorCode;

    public CustomAuthenticationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
