package com.seatrush.apigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 인증된 사용자가 권한이 없는 API에 접근하면 표준 403 응답을 반환합니다.
 */
@Component
public class JwtAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final SecurityErrorResponseWriter responseWriter;

    public JwtAccessDeniedHandler(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        return responseWriter.write(
                exchange,
                HttpStatus.FORBIDDEN,
                "AUTH005",
                "접근 권한이 없습니다."
        );
    }
}
