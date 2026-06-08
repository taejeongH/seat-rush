package com.seatrush.queueservice.common.response.status;

import org.springframework.http.HttpStatus;

public enum SuccessCode {
    OK(HttpStatus.OK, "GLOBAL200", "요청 응답에 성공했습니다."),
    CREATED(HttpStatus.CREATED, "GLOBAL201", "생성에 성공했습니다."),
    ACCEPTED(HttpStatus.ACCEPTED, "GLOBAL202", "요청이 접수되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    SuccessCode(HttpStatus httpStatus, String code, String message) {
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
        return true;
    }
}
