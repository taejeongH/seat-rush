package com.seatrush.ticketservice.common.entrytoken;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * entryToken 인터셉터의 대상 판별과 claims 전달을 검증합니다.
 */
class EntryTokenInterceptorTest {

    private EntryTokenValidator validator;
    private EntryTokenInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        validator = mock(EntryTokenValidator.class);
        interceptor = new EntryTokenInterceptor(validator);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    /**
     * entryToken 대상 API의 토큰을 검증하고 claims를 request attribute에 저장합니다.
     */
    @Test
    void validateTokenAndStoreClaimsForProtectedHandler() throws Exception {
        EntryTokenClaims claims = new EntryTokenClaims(
                "jti-1",
                10L,
                20L,
                Instant.now().plusSeconds(300)
        );
        when(request.getHeader("X-Entry-Token")).thenReturn("entry-token");
        when(request.getHeader("X-User-Id")).thenReturn("10");
        when(validator.validate("entry-token", 10L)).thenReturn(claims);

        boolean result = interceptor.preHandle(
                request,
                response,
                handlerMethod("protectedEndpoint")
        );

        assertThat(result).isTrue();
        verify(request).setAttribute(EntryTokenRequestAttribute.CLAIMS, claims);
    }

    /**
     * entryToken 대상이 아닌 API는 토큰 검증 없이 통과시킵니다.
     */
    @Test
    void skipValidationForUnprotectedHandler() throws Exception {
        boolean result = interceptor.preHandle(
                request,
                response,
                handlerMethod("publicEndpoint")
        );

        assertThat(result).isTrue();
    }

    /**
     * Gateway 사용자 헤더가 없으면 인증 필요 예외를 발생시킵니다.
     */
    @Test
    void rejectRequestWithoutUserHeader() throws Exception {
        when(request.getHeader("X-Entry-Token")).thenReturn("entry-token");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                response,
                handlerMethod("protectedEndpoint")
        ))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private HandlerMethod handlerMethod(String methodName) throws Exception {
        TestController controller = new TestController();
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    private static class TestController {

        @EntryTokenRequired
        public void protectedEndpoint() {
        }

        public void publicEndpoint() {
        }
    }
}
