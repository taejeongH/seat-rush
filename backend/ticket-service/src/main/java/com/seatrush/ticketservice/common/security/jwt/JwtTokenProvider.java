package com.seatrush.ticketservice.common.security.jwt;

import com.seatrush.ticketservice.domain.auth.entity.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 로그인에 성공한 사용자 정보를 바탕으로 RSA 서명(RS256)이 적용된 JWT Access Token을 발급하는 컴포넌트입니다.
 * 
 * 토큰에는 사용자의 고유 ID, 이메일, 시스템 권한(Role) 등의 Claim 세트가 포함되며,
 * 비대칭 키 알고리즘을 사용하므로 발급 시 Private Key로 서명하고 검증처(API Gateway 등)에서는 Public Key를 이용해 검증합니다.
 */
@Component
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * 인증된 사용자 객체(User) 정보를 추출하여 RS256 규격의 JWT Access Token을 발행합니다.
     *
     * @param user 인증된 회원 엔티티
     * @return 서명된 JWT 토큰 문자열
     */
    public String createAccessToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.accessTokenExpirationSeconds());
        
        // RS256 비대칭 키 서명을 사용하도록 헤더 설정
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        
        // 토큰 클레임 세트 빌드
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString()) // 사용자 고유 식별자 (Long String)
                .claim("email", user.getEmail())   // 이메일
                .claim("role", user.getRole().name()) // 회원 등급/역할 (USER, ADMIN 등)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 설정된 Access Token의 만료 시간(초 단위)을 조회합니다.
     *
     * @return 만료 시간 (초)
     */
    public long getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpirationSeconds();
    }
}

