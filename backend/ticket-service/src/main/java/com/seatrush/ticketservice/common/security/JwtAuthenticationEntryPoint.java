package com.seatrush.ticketservice.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 사용자가 보호된 API에 접근했을 때 401 응답을 처리합니다.
 * Security 필터의 예외는 GlobalExceptionHandler까지 전달되지 않으므로
 * 서비스의 표준 ApiResponse 형식으로 직접 응답합니다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        ErrorCode errorCode = getErrorCode(exception);

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.onFailure(errorCode).getBody());
    }

    /**
     * 지정된 인증 오류가 없으면 일반적인 미인증 오류로 처리합니다.
     */
    private ErrorCode getErrorCode(AuthenticationException exception) {
        if (exception instanceof CustomAuthenticationException customException) {
            return customException.getErrorCode();
        }

        return ErrorCode.AUTHENTICATION_REQUIRED;
    }
}
