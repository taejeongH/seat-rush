package com.seatrush.apigateway.common.response;

public record ErrorResponse(
        boolean isSuccess,
        String code,
        String message
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message);
    }
}
