package com.seatrush.ticketservice.common.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA PEM 키 파일을 읽어 JWT 발급용 JwtEncoder와 검증용 JwtDecoder를 구성합니다.
 * private key는 토큰 서명에 사용하고 public key는 토큰 검증에 사용합니다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * private key로 RS256 JWT를 서명할 JwtEncoder를 생성합니다.
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPublicKey publicKey = parsePublicKey(properties.publicKeyLocation());
        RSAPrivateKey privateKey = parsePrivateKey(properties.privateKeyLocation());

        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("JWT private key와 public key가 동일한 키쌍이 아닙니다.");
        }

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));

        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * public key로 JWT 서명, 만료 시간, issuer를 검증할 JwtDecoder를 생성합니다.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(parsePublicKey(properties.publicKeyLocation()))
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /**
     * PKCS#8 형식의 PEM 파일을 RSA private key로 변환합니다.
     */
    private RSAPrivateKey parsePrivateKey(Resource keyResource) {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(readKey(keyResource));
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodedKey));
            return (RSAPrivateKey) privateKey;
        } catch (Exception exception) {
            throw new IllegalStateException("JWT private key 설정을 확인해주세요.", exception);
        }
    }

    /**
     * X.509 형식의 PEM 파일을 RSA public key로 변환합니다.
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
     * classpath의 PEM 파일을 읽고 Base64 본문만 반환합니다.
     */
    private String readKey(Resource keyResource) {
        if (keyResource == null || !keyResource.exists()) {
            throw new IllegalStateException("JWT 키 파일이 존재하지 않습니다.");
        }

        try (InputStream inputStream = keyResource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 키 파일을 읽을 수 없습니다.", exception);
        }
    }
}
