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
 * entryToken이 필요한 API의 JWT와 요청 사용자 정보를 공통 검증합니다.
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
     * 대상 Handler의 entryToken을 검증하고 claims를 request attribute에 저장합니다.
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !requiresEntryToken(handlerMethod)) {
            return true;
        }

        String entryToken = request.getHeader(ENTRY_TOKEN_HEADER);
        Long userId = parseUserId(request.getHeader(USER_ID_HEADER));
        EntryTokenClaims claims = entryTokenValidator.validate(entryToken, userId);
        request.setAttribute(EntryTokenRequestAttribute.CLAIMS, claims);
        return true;
    }

    private boolean requiresEntryToken(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getMethod(),
                EntryTokenRequired.class
        ) || AnnotatedElementUtils.hasAnnotation(
                handlerMethod.getBeanType(),
                EntryTokenRequired.class
        );
    }

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
