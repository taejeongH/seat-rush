package com.seatrush.ticketservice.common.entrytoken;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * 대기열 서비스(Queue Service)가 발행한 대기열 진입 토큰(Entry Token)의 유효성을 검증하기 위한 JwtDecoder를 구성하는 클래스입니다.
 * 
 * 대기열 서비스의 RSA Public Key를 이용하여 서명을 검증하고,
 * 설정에 등록된 발급처(Issuer) 및 대상(Audience) 클레임의 유효성도 복합 검증합니다.
 */
@Configuration
@EnableConfigurationProperties(EntryTokenProperties.class)
public class EntryTokenConfig {

    /**
     * 대기열 진입용 JWT 토큰을 해독하고 검증(Issuer 및 Audience)하는 JwtDecoder 빈을 구성합니다.
     *
     * @param properties 대기열 진입 토큰 검증용 프로퍼티 정보
     * @return 대기열 토큰 검증용 JwtDecoder
     */
    @Bean
    public JwtDecoder entryTokenJwtDecoder(EntryTokenProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(parsePublicKey(properties.publicKeyLocation()))
                .build();

        // 발급자 검증자 설정
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
                
        // 대상 수신자(Audience) 클레임 검증자 설정
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>(
                        "aud",
                        audience -> audience != null && audience.contains(properties.audience())
                );
                
        // 다중 검증자 등록 (Issuer & Audience)
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator
        ));
        return decoder;
    }

    /**
     * 대기열 서비스의 RSA 공개키 PEM 파일을 파싱하여 RSAPublicKey 객체로 반환합니다.
     *
     * @param resource 공개키 파일 리소스
     * @return RSAPublicKey 객체
     * @throws IllegalStateException 파일이 없거나 읽을 수 없는 경우
     */
    private RSAPublicKey parsePublicKey(Resource resource) {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("entryToken public key 파일이 존재하지 않습니다.");
        }

        try (InputStream inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(content);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
            return (RSAPublicKey) publicKey;
        } catch (Exception exception) {
            throw new IllegalStateException("entryToken public key 설정을 확인해주세요.", exception);
        }
    }
}
