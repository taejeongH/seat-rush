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
 * Ticket Service의 public key로 accessToken을 검증할 JwtDecoder를 구성합니다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withPublicKey(parsePublicKey(properties.publicKeyLocation()))
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

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
