package com.seatrush.paymentservice.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * 요청과 응답 Body를 로깅할 수 있도록 Servlet request/response를 캐싱 래퍼로 감쌉니다.
 *
 * Body stream은 한 번 읽으면 소모되므로, 컨트롤러와 로깅 인터셉터가 모두 읽을 수 있게 필터 단계에서 래핑합니다.
 */
@Component
public class RequestCachingFilter extends OncePerRequestFilter {

    /**
     * 요청 Body는 CustomHttpRequestWrapper에 저장하고, 응답 Body는 ContentCachingResponseWrapper에 저장합니다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CustomHttpRequestWrapper requestWrapper = new CustomHttpRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            // 캐싱된 응답 Body를 실제 클라이언트 응답 스트림에 다시 복사합니다.
            responseWrapper.copyBodyToResponse();
        }
    }
}
