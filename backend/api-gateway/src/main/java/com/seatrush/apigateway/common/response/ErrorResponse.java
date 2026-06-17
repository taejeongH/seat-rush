package com.seatrush.apigateway.common.response;

/**
 * API Gateway에서 클라이언트에 반환할 공통 에러 응답 객체입니다.
 * 
 * @param isSuccess 요청 성공 여부 (에러 응답이므로 항상 false)
 * @param code 에러 코드 (예: AUTH003, AUTH004 등)
 * @param message 사용자 친화적인 에러 메시지
 */
public record ErrorResponse(
        boolean isSuccess,
        String code,
        String message
) {

    /**
     * 에러 코드와 메시지를 받아 ErrorResponse 객체를 생성합니다.
     *
     * @param code 에러 코드
     * @param message 에러 메시지
     * @return 생성된 ErrorResponse 객체
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message);
    }
}

