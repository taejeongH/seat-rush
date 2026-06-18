package com.seatrush.ticketservice.common.entrytoken;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 대기열 진입 토큰 검증이 필요한 Controller 메서드에 대해 pre-handle 인터셉터를 적용합니다.
 * 
 * Target 메서드나 클래스에 {@link EntryTokenRequired} 어노테이션이 부착된 경우,
 * 요청 헤더의 `X-Entry-Token`과 `X-User-Id`를 검증하고 디코딩된 클레임 정보를 HttpServletRequest 속성에 등록합니다.
 */
@Component
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final EntryTokenValidator entryTokenValidator;

    public EntryTokenInterceptor(EntryTokenValidator entryTokenValidator) {
        this.entryTokenValidator = entryTokenValidator;
    }

    /**
     * 컨트롤러 수행 전 요청 헤더로부터 대기열 진입용 토큰(X-Entry-Token)과 사용자 ID(X-User-Id)를 획득하여 유효성을 검증합니다.
     * 검증에 성공하면 JWT 내부 클레임을 {@code EntryTokenRequestAttribute.CLAIMS} 속성으로 Request에 바인딩하여 
     * 이후 비즈니스 로직(예: Controller)에서 꺼내 쓸 수 있도록 돕습니다.
     *
     * @param request 현재 HTTP 요청
     * @param response 현재 HTTP 응답
     * @param handler 실행할 핸들러 객체
     * @return 핸들러 실행 지속 여부 (true인 경우 다음 인터셉터나 컨트롤러로 진행)
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        // MVC 핸들러가 아니거나 대기열 진입 토큰 검증이 필요하지 않은 경로라면 바로 패스
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !requiresEntryToken(handlerMethod)) {
            return true;
        }

        String entryToken = request.getHeader(ENTRY_TOKEN_HEADER);
        Long userId = parseUserId(request.getHeader(USER_ID_HEADER));
        
        // 대기열 진입 토큰 유효성 검증
        EntryTokenClaims claims = entryTokenValidator.validate(entryToken, userId);
        
        // 컨트롤러단에서 사용할 수 있도록 요청 정보 속성에 주입
        request.setAttribute(EntryTokenRequestAttribute.CLAIMS, claims);
        return true;
    }

    /**
     * 실행 대상 핸들러 메서드 또는 컨트롤러 클래스에 {@link EntryTokenRequired} 어노테이션이 부착되어 있는지 판별합니다.
     */
    private boolean requiresEntryToken(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getMethod(),
                EntryTokenRequired.class
        ) || AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getBeanType(),
                EntryTokenRequired.class
        );
    }

    /**
     * X-User-Id 헤더 문자열 값을 파싱하여 Long 타입으로 변환합니다.
     * 
     * @param value 헤더 값 문자열
     * @return 사용자 고유 식별자 ID
     * @throws CustomException 인증 관련 헤더가 없거나 숫자 형식이 아닌 경우
     */
    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}

