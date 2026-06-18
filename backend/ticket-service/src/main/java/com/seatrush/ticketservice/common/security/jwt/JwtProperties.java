package com.seatrush.ticketservice.common.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * application.yml 에 설정된 `jwt` 프리픽스의 키 및 토큰 속성값을 바인딩하는 프로퍼티 레코드 클래스입니다.
 * 
 * @param issuer 토큰 발급주체 식별자
 * @param privateKeyLocation Access Token 서명(암호화)에 사용할 RSA Private Key 파일의 리소스 경로
 * @param publicKeyLocation Access Token 검증(복호화)에 사용할 RSA Public Key 파일의 리소스 경로
 * @param accessTokenExpirationSeconds Access Token의 유효 시간 (초 단위)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        Resource privateKeyLocation,
        Resource publicKeyLocation,
        long accessTokenExpirationSeconds
) {
}

