package com.seatrush.apigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 토큰이 없거나 유효하지 않은 요청을 표준 401 응답으로 변환합니다.
 */
@Component
public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    public JwtAuthenticationEntryPoint(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

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
