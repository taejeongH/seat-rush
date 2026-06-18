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
 * 외부에서 전달된 사용자 관련 헤더를 제거하고, 검증된 JWT 정보로 안전하게 재설정하여 전달하는 글로벌 필터입니다.
 * 
 * 외부 클라이언트가 직접 헤더(X-User-Id, X-User-Role)를 조작하여 전송하는 'Header Spoofing' 공격을 방지하기 위해,
 * 1) 들어오는 모든 요청에 대해 관련 헤더를 선제적으로 제거(Sanitize)합니다.
 * 2) 그 후 Spring Security Context에 보관된 JWT 토큰 검증 결과를 확인하여 해당 사용자의 ID와 역할(Role) 정보를 헤더에 재설정합니다.
 */
@Component
public class AuthenticatedUserHeaderFilter implements GlobalFilter, Ordered {

    /**
     * 다운스트림 마이크로서비스로 전달할 로그인 사용자의 식별자 헤더명
     */
    public static final String USER_ID_HEADER = "X-User-Id";

    /**
     * 다운스트림 마이크로서비스로 전달할 로그인 사용자의 권한/역할 헤더명
     */
    public static final String USER_ROLE_HEADER = "X-User-Role";

    /**
     * 들어오는 요청을 가로채서 보안 처리를 적용합니다.
     * 
     * @param exchange 현재 서버 웹 교환 객체 (요청/응답 컨텍스트)
     * @param chain 게이트웨이 필터 체인
     * @return 필터 처리 완료 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 위조 방지를 위해 기존의 X-User-Id, X-User-Role 헤더 제거
        ServerWebExchange sanitizedExchange = removeTrustedHeaders(exchange);

        // 2. 인증 객체(JwtAuthenticationToken)로부터 정보를 꺼내와 헤더에 안전하게 세팅
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

    /**
     * 필터 실행 순서를 지정합니다.
     * Spring Security의 인증 필터 이후에 실행되어 정상 인증 정보에 접근할 수 있도록 하고,
     * 다운스트림 서비스로 요청이 전달되기 전에 헤더를 가공할 수 있도록 순서를 조정합니다.
     * 
     * @return 필터 실행 우선순위 (낮을수록 먼저 실행됨, 여기서는 -1)
     */
    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * 요청에서 외부로부터 주입되었을 가능성이 있는 보안 헤더(X-User-Id, X-User-Role)를 강제로 삭제합니다.
     *
     * @param exchange 현재 웹 교환 객체
     * @return 헤더가 제거된 새로운 ServerWebExchange 인스턴스
     */
    private ServerWebExchange removeTrustedHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                }))
                .build();
    }

    /**
     * JWT 검증을 거쳐 추출된 사용자 ID 및 역할 정보를 요청 헤더에 안전하게 추가합니다.
     *
     * @param exchange 헤더가 정제된 웹 교환 객체
     * @param userId 검증된 사용자의 ID (Subject)
     * @param role 검증된 사용자의 권한 (Claim 'role')
     * @return 헤더가 새로 주입된 ServerWebExchange 인스턴스
     */
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

