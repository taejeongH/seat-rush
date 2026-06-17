package com.seatrush.apigateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * API Gateway 레벨의 스프링 시큐리티(WebFlux Reactive Security) 환경을 설정하는 클래스입니다.
 * 
 * 허용된 공개 API 경로(permitAll)를 정의하고,
 * 그 외 모든 요청에 대해서는 유효한 JWT(Bearer Token)가 존재해야 접근이 가능하도록 가드역할을 수행합니다.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * HTTP 요청에 대한 보안 필터 체인 규칙을 정의합니다.
     * WebFlux Reactive 비동기 환경에 맞춰 구성되었습니다.
     * 
     * - CSRF 비활성화: REST API 서버로서 JWT 토큰 기반 인증을 사용하므로 비활성화합니다.
     * - Form 로그인 및 HTTP Basic 인증 비활성화: 세션 관리 없이 무상태(Stateless)로 통신합니다.
     * - ExceptionHandling: 인증 실패(401) 및 인가 실패(403) 시 커스텀 진입점/핸들러를 태우도록 지정합니다.
     * - AuthorizeExchange:
     *   - OPTIONS (CORS Preflight) 전체 허용
     *   - /api/admin/** 은 ADMIN 권한(ROLE_ADMIN) 필요
     *   - /api/auth/** (로그인/회원가입 등) 전체 허용
     *   - 콘서트 목록 조회, 좌석 레이아웃 조회, 연습 세션 시작/종료 전체 허용
     *   - 헬스 체크(/health) 및 에러 경로 전체 허용
     *   - 그 외 모든 요청은 인증 필요
     * - OAuth2 Resource Server: JWT 기반 검증 및 권한 변환기를 등록합니다.
     *
     * @param http ServerHttpSecurity 인스턴스
     * @return 구성된 SecurityWebFilterChain 빈 객체
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/concerts/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/seat-layouts/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/practice/queues/sessions").permitAll()
                        .pathMatchers(HttpMethod.DELETE, "/api/practice/queues/sessions/**").permitAll()
                        .pathMatchers("/health", "/error").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * JWT 토큰 안의 클레임 중 "role"에 해당하는 값(예: USER, ADMIN)을 읽어
     * Spring Security의 Standard 권한 포맷인 "ROLE_USER", "ROLE_ADMIN" 형태로 어댑팅 및 매핑해주는 변환기입니다.
     *
     * @return ReactiveJwtAuthenticationConverterAdapter 어댑터 객체
     */
    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(authenticationConverter);
    }
}

