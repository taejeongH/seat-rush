package com.seatrush.ticketservice.common.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * application.yml의 jwt 설정값을 타입이 있는 객체로 제공합니다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        Resource privateKeyLocation,
        Resource publicKeyLocation,
        long accessTokenExpirationSeconds
) {
}
