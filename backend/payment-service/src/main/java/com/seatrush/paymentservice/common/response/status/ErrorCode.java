package com.seatrush.paymentservice.common.response.status;

import org.springframework.http.HttpStatus;

/**
 * Payment Service에서 사용하는 에러 코드와 사용자 응답 메시지를 정의합니다.
 */
public enum ErrorCode {
    // COMMON
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON002", "서버 내부 오류가 발생했습니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON003", "존재하지 않는 리소스입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON004", "지원하지 않는 HTTP 메서드입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON005", "이미 존재하는 리소스입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON006", "잘못된 요청입니다."),

    // PAYMENT
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAYMENT003", "해당 예매의 결제가 이미 존재합니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT004", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PAYMENT005", "해당 결제에 접근할 수 없습니다."),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "PAYMENT006", "이미 다른 결과로 완료된 결제입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return false;
    }
}
