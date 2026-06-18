package com.seatrush.apigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 토큰 자체는 유효하지만, 해당 사용자가 요청한 리소스(API)에 접근하기 위해 필요한 인가 권한(Role)이 부족한 경우 호출되는 핸들러입니다.
 * 
 * 예: 일반 사용자가 /api/admin/** 등의 관리자 전용 API로 접근하는 경우
 * HTTP 403 Forbidden 상태 코드와 함께 공통 규격인 ErrorResponse("AUTH005", "접근 권한이 없습니다.") 형식을 직렬화하여 반환합니다.
 */
@Component
public class JwtAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final SecurityErrorResponseWriter responseWriter;

    public JwtAccessDeniedHandler(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    /**
     * 인가 예외(AccessDeniedException) 발생 시 클라이언트에 공통 에러 포맷으로 에러 응답을 전송합니다.
     *
     * @param exchange 현재 서버 웹 교환 객체
     * @param exception 인가 실패 예외
     * @return 에러 응답 전송 완료 Mono
     */
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

