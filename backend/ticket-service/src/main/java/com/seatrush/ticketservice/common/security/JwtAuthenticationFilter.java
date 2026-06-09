package com.seatrush.ticketservice.common.security;

import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.auth.entity.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 요청의 Bearer accessToken을 검증하고 현재 요청의 인증 정보를 설정하는 필터입니다.
 * 인증 정보는 SecurityContext에 요청 동안만 저장되며 서버 세션에는 저장되지 않습니다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtDecoder jwtDecoder,
            JwtAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = resolveAccessToken(request);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(accessToken);
            AuthenticatedUser user = createAuthenticatedUser(jwt);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new CustomAuthenticationException(ErrorCode.INVALID_ACCESS_TOKEN)
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 Bearer 토큰을 추출합니다.
     */
    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        return accessToken.isEmpty() ? null : accessToken;
    }

    /**
     * 검증된 JWT claim을 SecurityContext에 저장할 사용자 정보로 변환합니다.
     */
    private AuthenticatedUser createAuthenticatedUser(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        UserRole role = UserRole.valueOf(jwt.getClaimAsString("role"));

        return new AuthenticatedUser(userId, email, role);
    }
}
