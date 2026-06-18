package com.seatrush.apigateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * application.yml의 jwt 설정을 바인딩하는 설정 객체입니다.
 *
 * issuer는 토큰 발급자를 검증하는 데 사용하고,
 * publicKeyLocation은 RSA 공개키 PEM 리소스를 읽는 데 사용합니다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        Resource publicKeyLocation
) {
}
