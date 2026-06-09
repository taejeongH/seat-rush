package com.seatrush.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 외부에서 전달된 사용자 헤더를 제거하고 검증된 JWT 정보로 다시 설정합니다.
 */
@Component
public class AuthenticatedUserHeaderFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = removeTrustedHeaders(exchange);

        return sanitizedExchange.getPrincipal()
                .cast(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> {
                    String userId = authentication.getToken().getSubject();
                    String role = authentication.getToken().getClaimAsString("role");
                    return addTrustedHeaders(sanitizedExchange, userId, role);
                })
                .defaultIfEmpty(sanitizedExchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private ServerWebExchange removeTrustedHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                }))
                .build();
    }

    private ServerWebExchange addTrustedHeaders(
            ServerWebExchange exchange,
            String userId,
            String role
    ) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.set(USER_ID_HEADER, userId);
                    headers.set(USER_ROLE_HEADER, role);
                }))
                .build();
    }
}
