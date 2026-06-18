package com.seatrush.paymentservice.common.exception;

import com.seatrush.paymentservice.common.response.ApiResponse;
import com.seatrush.paymentservice.common.response.status.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Payment Service 전역 예외를 공통 API 응답 형식으로 변환합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_LOG_FORMAT = "[{}] {} {} - {}";

    /**
     * 도메인에서 의도적으로 발생시킨 CustomException을 처리합니다.
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        logWarn(errorCode, request, exception.getMessage());

        return ApiResponse.onFailure(errorCode);
    }

    /**
     * 요청 본문, validation, 파라미터 바인딩 오류를 잘못된 입력값 응답으로 처리합니다.
     */
    @ExceptionHandler({
            BindException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception exception, HttpServletRequest request) {
        String detail = summarizeInvalidInput(exception);
        logWarn(ErrorCode.INVALID_INPUT_VALUE, request, detail);

        return ApiResponse.onFailure(ErrorCode.INVALID_INPUT_VALUE);
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청을 처리합니다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        logWarn(ErrorCode.METHOD_NOT_ALLOWED, request, exception.getMessage());

        return ApiResponse.onFailure(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 예상하지 못한 예외를 서버 오류로 처리합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception, HttpServletRequest request) {
        log.error(
                REQUEST_LOG_FORMAT,
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage(),
                exception
        );

        return ApiResponse.onFailure(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * validation 오류 로그에 남길 핵심 메시지를 추출합니다.
     */
    private static String summarizeInvalidInput(Exception exception) {
        if (exception instanceof BindException bindException) {
            return bindException.getBindingResult().getFieldErrors().stream()
                    .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("입력값 검증에 실패했습니다.");
        }

        if (exception instanceof HttpMessageNotReadableException) {
            return "요청 본문을 읽을 수 없습니다.";
        }

        String message = exception.getMessage();
        return (message == null || message.isBlank()) ? "입력값 검증에 실패했습니다." : message;
    }

    private static void logWarn(ErrorCode errorCode, HttpServletRequest request, String detail) {
        log.warn(REQUEST_LOG_FORMAT, errorCode.getCode(), request.getMethod(), request.getRequestURI(), detail);
    }
}
