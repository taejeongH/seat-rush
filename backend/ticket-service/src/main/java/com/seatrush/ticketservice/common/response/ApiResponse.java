package com.seatrush.ticketservice.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import org.springframework.http.ResponseEntity;

public record ApiResponse<T>(
        Boolean isSuccess,
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        T result
) {

    public static <T> ResponseEntity<ApiResponse<T>> onSuccess(SuccessCode code, T result) {
        ApiResponse<T> body = new ApiResponse<>(
                code.isSuccess(),
                code.getCode(),
                code.getMessage(),
                result
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    public static ResponseEntity<ApiResponse<Void>> onSuccess(SuccessCode code) {
        ApiResponse<Void> body = new ApiResponse<>(
                code.isSuccess(),
                code.getCode(),
                code.getMessage(),
                null
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    public static ResponseEntity<ApiResponse<Void>> onFailure(ErrorCode code) {
        ApiResponse<Void> body = new ApiResponse<>(
                code.isSuccess(),
                code.getCode(),
                code.getMessage(),
                null
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    public static ResponseEntity<ApiResponse<Void>> onFailure(ErrorCode code, String message) {
        ApiResponse<Void> body = new ApiResponse<>(
                code.isSuccess(),
                code.getCode(),
                message,
                null
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    public static <T> ResponseEntity<ApiResponse<T>> onFailure(ErrorCode code, String message, T result) {
        ApiResponse<T> body = new ApiResponse<>(
                code.isSuccess(),
                code.getCode(),
                message,
                result
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }
}
