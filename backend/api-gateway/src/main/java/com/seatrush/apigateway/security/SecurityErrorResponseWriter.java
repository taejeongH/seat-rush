package com.seatrush.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatrush.apigateway.common.response.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security 예외(인증/인가 실패)가 발생했을 때 필터 레이어 단에서
 * Gateway 전체 공통 에러 포맷인 {@link ErrorResponse} 객체를 JSON 문자열로 직렬화하여 클라이언트에 직접 작성해 주는 라이터 컴포넌트입니다.
 */
@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * HTTP 응답 스트림에 에러 코드와 메시지를 JSON 데이터로 직접 씁니다.
     * WebFlux 환경의 DataBuffer를 생성하여 논블로킹 방식으로 응답을 전송합니다.
     *
     * @param exchange 현재 서버 웹 교환 객체
     * @param status 응답할 HTTP 상태 코드
     * @param code 비즈니스 에러 코드 (예: AUTH003, AUTH005 등)
     * @param message 클라이언트에게 보여줄 에러 상세 메시지
     * @return 작성 완료 신호를 보내는 Mono
     */
    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message
    ) {
        // 공통 에러 바디 생성 및 직렬화
        byte[] body = serialize(ErrorResponse.of(code, message));

        // HTTP 응답 헤더 및 상태 코드 세팅
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);

        // DataBuffer로 변환하여 비동기 응답 스트림에 작성
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * ErrorResponse 객체를 JSON 바이트 배열로 직렬화합니다.
     * 직렬화 도중 예외가 발생할 경우 하드코딩된 기본 서버 에러 JSON(COMMON002)을 대신 반환하여 예외 처리가 무한 루프에 빠지거나 깨지는 것을 막습니다.
     *
     * @param response 직렬화할 ErrorResponse 객체
     * @return JSON 바이트 배열
     */
    private byte[] serialize(ErrorResponse response) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (JsonProcessingException exception) {
            // ObjectMapper 직렬화 실패 시 기본 복구용 JSON 메시지 반환
            return """
                    {"isSuccess":false,"code":"COMMON002","message":"서버 내부 에러가 발생했습니다."}
                    """.trim().getBytes(StandardCharsets.UTF_8);
        }
    }
}

