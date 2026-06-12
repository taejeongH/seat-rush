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
 * Queue Service 공개키로 entryToken을 검증할 JwtDecoder를 구성합니다.
 */
@Configuration
@EnableConfigurationProperties(EntryTokenProperties.class)
public class EntryTokenConfig {

    @Bean
    public JwtDecoder entryTokenJwtDecoder(EntryTokenProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(parsePublicKey(properties.publicKeyLocation()))
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>(
                        "aud",
                        audience -> audience != null && audience.contains(properties.audience())
                );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator
        ));
        return decoder;
    }

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
