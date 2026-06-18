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
 * HTTP ?”ì²­(Request) ë°”ë””?€ ?‘ë‹µ(Response) ë°”ë””ë¥??¤ì¤‘ ë¡œê¹…???„í•´ ìºì‹±?????ˆë„ë¡??œë¸”ë¦??˜í¼ ê°ì²´ë¡?ê°ì‹¸???œë¸”ë¦??„í„° ?´ë˜?¤ì…?ˆë‹¤.
 *
 * OncePerRequestFilterë¥??•ì¥?˜ì—¬ ?˜ë‚˜??HTTP ?”ì²­??????ë²ˆë§Œ ?„í„°ë§ì´ ?˜í–‰?˜ë„ë¡?ë³´ì¥?©ë‹ˆ??
 */
@Component
public class RequestCachingFilter extends OncePerRequestFilter {

    /**
     * HTTP ?”ì²­??CustomHttpRequestWrapperë¡??˜í•‘?˜ì—¬ Input Stream???¬ë…??ê°€?¥í•˜ê²?ìºì‹±?˜ê³ ,
     * HTTP ?‘ë‹µ??ContentCachingResponseWrapperë¡??˜í•‘?˜ì—¬ ?‘ë‹µ ë³¸ë¬¸??ìºì‹±?©ë‹ˆ??
     *
     * @param request ?œë¸”ë¦??”ì²­ ê°ì²´
     * @param response ?œë¸”ë¦??‘ë‹µ ê°ì²´
     * @param filterChain ?„í„° ì²´ì¸ ê°ì²´
     * @throws ServletException ?œë¸”ë¦??ˆì™¸ ë°œìƒ ??
     * @throws IOException ?…ì¶œ???ˆì™¸ ë°œìƒ ??
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
            // ìºì‹±???‘ë‹µ ë°”ë””ë¥??ë˜???œë¸”ë¦?ì¶œë ¥ ?¤íŠ¸ë¦¼ìœ¼ë¡?ìµœì¢… ë³µì‚¬?©ë‹ˆ??
            responseWrapper.copyBodyToResponse();
        }
    }
}
