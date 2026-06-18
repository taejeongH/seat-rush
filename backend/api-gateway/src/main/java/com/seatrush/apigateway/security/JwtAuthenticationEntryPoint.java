package com.seatrush.apigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 인증정보(JWT 토큰)가 누락되었거나, 전달된 토큰이 유효하지 않은 비인증 상태의 요청이 보호된 API에 접근할 때 진입하게 되는 클래스입니다.
 * 
 * 예외 타입에 맞춰서 구체적인 에러 코드를 내려줍니다:
 * - 만료, 서명 불일치 등 유효하지 않은 토큰: AUTH004 ("유효하지 않은 accessToken입니다.")
 * - 토큰 누락 등의 비인증 접근: AUTH003 ("인증이 필요합니다.")
 * 이들은 모두 HTTP 401 Unauthorized 상태 코드로 응답합니다.
 */
@Component
public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    public JwtAuthenticationEntryPoint(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    /**
     * 인증되지 않은 사용자 접근(AuthenticationException) 시 예외 상태에 대응하는 공통 에러 응답을 작성하여 클라이언트로 반환합니다.
     *
     * @param exchange 현재 서버 웹 교환 객체
     * @param exception 인증 실패 관련 예외
     * @return 에러 응답 전송 완료 Mono
     */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        if (exception instanceof InvalidBearerTokenException) {
            return responseWriter.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "AUTH004",
                    "유효하지 않은 accessToken입니다."
            );
        }

        return responseWriter.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "AUTH003",
                "인증이 필요합니다."
        );
    }
}

