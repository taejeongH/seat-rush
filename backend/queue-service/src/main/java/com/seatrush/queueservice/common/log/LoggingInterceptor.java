package com.seatrush.queueservice.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = "startTime";

    private final ObjectMapper objectMapper;

    public LoggingInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        logPathVariables(request, handler);
        logQueryParameters(request);
        logRequestBody(request);

        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        long durationMs = getDurationMs(request);
        int status = response.getStatus();

        if (status >= 400) {
            log.error("[{}] {} request failed - {}ms | status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    durationMs,
                    status);
            return;
        }

        log.info("[{}] {} request completed - {}ms | status={}",
                request.getMethod(),
                request.getRequestURI(),
                durationMs,
                status);
    }

    private void logPathVariables(HttpServletRequest request, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return;
        }

        Map<String, String> pathVariables = getPathVariables(request);

        if (pathVariables != null && !pathVariables.isEmpty()) {
            log.info("[{}] {} pathVars={}", request.getMethod(), request.getRequestURI(), pathVariables);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getPathVariables(HttpServletRequest request) {
        return (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    }

    private void logQueryParameters(HttpServletRequest request) {
        if (request.getParameterNames().hasMoreElements()) {
            log.info("[{}] {} queryParams={}", request.getMethod(), request.getRequestURI(), getRequestParams(request));
        }
    }

    private void logRequestBody(HttpServletRequest request) {
        CustomHttpRequestWrapper requestWrapper = WebUtils.getNativeRequest(request, CustomHttpRequestWrapper.class);

        if (requestWrapper == null) {
            return;
        }

        String body = new String(requestWrapper.getRequestBody(), StandardCharsets.UTF_8);

        if (body.isBlank()) {
            return;
        }

        try {
            Object json = objectMapper.readValue(body, Object.class);
            String prettyBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            log.info("[{}] {} body={}", request.getMethod(), request.getRequestURI(), prettyBody);
        } catch (Exception exception) {
            log.info("[{}] {} body(raw)={}", request.getMethod(), request.getRequestURI(), body);
        }
    }

    private long getDurationMs(HttpServletRequest request) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);

        if (startTime instanceof Long start) {
            return System.currentTimeMillis() - start;
        }

        return 0L;
    }

    private Map<String, String> getRequestParams(HttpServletRequest request) {
        Map<String, String> paramMap = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            paramMap.put(paramName, request.getParameter(paramName));
        }

        return paramMap;
    }
}
