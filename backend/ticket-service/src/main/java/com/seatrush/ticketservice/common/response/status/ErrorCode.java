package com.seatrush.ticketservice.common.response.status;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON002", "서버 내부 에러가 발생했습니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON003", "존재하지 않는 리소스입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON004", "지원하지 않는 HTTP 메서드입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON005", "이미 존재하는 리소스입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON006", "잘못된 요청입니다."),
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONCERT001", "공연을 찾을 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE001", "회차를 찾을 수 없습니다."),
    INVALID_SCHEDULE_PERIOD(HttpStatus.BAD_REQUEST, "SCHEDULE002", "회차 시간 설정이 올바르지 않습니다."),
    CANCELED_SCHEDULE(HttpStatus.CONFLICT, "SCHEDULE003", "취소된 회차는 변경할 수 없습니다."),
    SCHEDULE_UPDATE_CONFLICT(HttpStatus.CONFLICT, "SCHEDULE004", "회차가 다른 요청에 의해 변경되었습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH003", "인증이 필요합니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH004", "유효하지 않은 accessToken입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH005", "접근 권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER001", "사용자를 찾을 수 없습니다."),
    INVALID_ENTRY_TOKEN(HttpStatus.UNAUTHORIZED, "ENTRY_TOKEN001", "유효하지 않거나 만료된 entryToken입니다."),
    ENTRY_TOKEN_USER_MISMATCH(HttpStatus.FORBIDDEN, "ENTRY_TOKEN002", "entryToken의 사용자 정보가 일치하지 않습니다."),
    ENTRY_TOKEN_SCHEDULE_MISMATCH(HttpStatus.FORBIDDEN, "ENTRY_TOKEN003", "entryToken의 회차 정보가 일치하지 않습니다.");

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
