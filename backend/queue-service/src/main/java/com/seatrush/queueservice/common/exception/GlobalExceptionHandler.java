package com.seatrush.queueservice.common.exception;

import com.seatrush.queueservice.common.response.ApiResponse;
import com.seatrush.queueservice.common.response.status.ErrorCode;
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

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_LOG_FORMAT = "[{}] {} {} - {}";

    // 비즈니스 로직에서 의도적으로 발생시킨 예외를 표준 에러 응답으로 변환한다.
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        logWarn(errorCode, request, exception.getMessage());

        return ApiResponse.onFailure(errorCode);
    }

    // 요청 파라미터, 요청 본문, 검증 실패 예외를 입력값 오류 응답으로 통일한다.
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

    // 지원하지 않는 HTTP 메서드 요청을 405 에러 응답으로 변환한다.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        logWarn(ErrorCode.METHOD_NOT_ALLOWED, request, exception.getMessage());

        return ApiResponse.onFailure(ErrorCode.METHOD_NOT_ALLOWED);
    }

    // 예상하지 못한 모든 예외를 서버 내부 오류 응답으로 변환한다.
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

    // 입력값 오류 예외에서 로그에 남길 핵심 메시지를 추출한다.
    private static String summarizeInvalidInput(Exception exception) {
        if (exception instanceof BindException bindException) {
            return bindException.getBindingResult().getFieldErrors().stream()
                    .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("잘못된 입력값입니다.");
        }

        if (exception instanceof HttpMessageNotReadableException) {
            return "요청 본문 형식을 확인해주세요.";
        }

        String message = exception.getMessage();
        return (message == null || message.isBlank()) ? "잘못된 입력값입니다." : message;
    }

    private static void logWarn(ErrorCode errorCode, HttpServletRequest request, String detail) {
        log.warn(REQUEST_LOG_FORMAT, errorCode.getCode(), request.getMethod(), request.getRequestURI(), detail);
    }
}
