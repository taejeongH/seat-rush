package com.seatrush.ticketservice.common.exception;

import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
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
 * 애플리케이션 전체(Controller 레이어 및 비즈니스 로직)에서 발생하는 모든 예외를 포착하여
 * 클라이언트에게 표준화된 공통 에러 응답({@link ApiResponse})을 돌려주는 전역 예외 처리기입니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_LOG_FORMAT = "[{}] {} {} - {}";

    /**
     * 비즈니스 로직 내부에서 의도적으로 발생시킨 CustomException 예외를 처리합니다.
     * 에러 코드에 따라 4xx/5xx ResponseEntity와 에러 메시지를 응답합니다.
     *
     * @param exception 커스텀 비즈니스 예외
     * @param request 현재 HTTP 요청 서블릿 객체
     * @return 에러 사유를 담은 공통 응답 엔티티
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        logWarn(errorCode, request, exception.getMessage());

        return ApiResponse.onFailure(errorCode);
    }

    /**
     * @Valid 또는 @Validated 유효성 검증 실패(MethodArgumentNotValidException, BindException) 및
     * JSON 바인딩 실패(HttpMessageNotReadableException) 등 입력 데이터 규격 오류 예외를 처리합니다.
     * 
     * 잘못된 필드와 상세 검증 실패 사유를 취합하여 로그에 남기고, INVALID_INPUT_VALUE 상태 코드로 응답합니다.
     *
     * @param exception 입력 형식 위반 예외
     * @param request 현재 HTTP 요청 서블릿 객체
     * @return 400 Bad Request 공통 응답 엔티티
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
     * 지원하지 않는 HTTP Method(GET, POST, DELETE 등)로 보호된 리소스에 요청이 올 경우 405 Method Not Allowed 응답으로 변환합니다.
     *
     * @param exception 지원 불가 HTTP 메서드 예외
     * @param request 현재 HTTP 요청 서블릿 객체
     * @return 405 Method Not Allowed 공통 응답 엔티티
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
     * 개발자가 사전에 예측하지 못해 포착되지 않은 최상위 Exception 예외를 통합 처리합니다.
     * 오류 원인 추적을 위해 StackTrace를 로깅하고, 500 Internal Server Error 상태 코드로 응답합니다.
     *
     * @param exception 예측 불가능했던 시스템 예외
     * @param request 현재 HTTP 요청 서블릿 객체
     * @return 500 Internal Server Error 공통 응답 엔티티
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
     * 복수의 입력 데이터 검증 실패 요인을 문자열 하나로 결합(Summarize)합니다.
     *
     * @param exception 발생한 바인딩/검증 예외
     * @return 에러 메시지 요약본
     */
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

    /**
     * 경고성 예외 정보를 콘솔 및 로그에 정형화된 규격으로 출력합니다.
     */
    private static void logWarn(ErrorCode errorCode, HttpServletRequest request, String detail) {
        log.warn(REQUEST_LOG_FORMAT, errorCode.getCode(), request.getMethod(), request.getRequestURI(), detail);
    }
}

