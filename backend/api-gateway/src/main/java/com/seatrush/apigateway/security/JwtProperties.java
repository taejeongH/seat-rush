package com.seatrush.apigateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * API Gateway의 JWT 검증 설정을 제공합니다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        Resource publicKeyLocation
) {
}
