package com.seatrush.apigateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ticket Service(인증 및 예매 서비스)의 RSA 공개 키(Public Key)를 사용하여 JWT Access Token을 검증하는 JwtDecoder를 설정합니다.
 * 
 * 비동기 논블로킹(Reactive WebFlux) 환경에 맞춰 ReactiveJwtDecoder 구현체를 빈으로 구성하며,
 * PEM 파일 포맷의 RSA 공개 키를 해석하여 Spring Security OAuth2 검증기에 주입합니다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * Nimbus 기반의 ReactiveJwtDecoder를 설정하여 들어오는 JWT의 유효성을 체크합니다.
     * 발급자(Issuer) 정보 검증 설정도 함께 주입합니다.
     *
     * @param properties JWT 설정 매핑 프로퍼티
     * @return ReactiveJwtDecoder 인스턴스
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withPublicKey(parsePublicKey(properties.publicKeyLocation()))
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /**
     * 파일 리소스(PEM)로부터 공개키 바이트를 읽어와 RSAPublicKey 객체로 파싱합니다.
     *
     * @param keyResource 공개 키 PEM 파일 리소스
     * @return 생성된 RSAPublicKey 객체
     * @throws IllegalStateException 파싱 중 에러 발생 시
     */
    private RSAPublicKey parsePublicKey(Resource keyResource) {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(readKey(keyResource));
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodedKey));
            return (RSAPublicKey) publicKey;
        } catch (Exception exception) {
            throw new IllegalStateException("JWT public key 설정을 확인해주세요.", exception);
        }
    }

    /**
     * PEM 파일에서 BEGIN/END 헤더 및 줄바꿈 문자를 제외하고 순수 Base64 인코딩 스트링만 추출합니다.
     *
     * @param keyResource 공개 키 PEM 파일 리소스
     * @return 줄바꿈 및 헤더가 제거된 PEM 문자열
     * @throws IllegalStateException 파일을 읽을 수 없거나 존재하지 않는 경우
     */
    private String readKey(Resource keyResource) {
        if (keyResource == null || !keyResource.exists()) {
            throw new IllegalStateException("JWT public key 파일이 존재하지 않습니다.");
        }

        try (InputStream inputStream = keyResource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
        } catch (Exception exception) {
            throw new IllegalStateException("JWT public key 파일을 읽을 수 없습니다.", exception);
        }
    }
}

