package com.seatrush.queueservice.domain.entrytoken.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtEncoder;
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
 * entryToken 전용 RSA 키로 JWT를 서명할 JwtEncoder를 구성합니다.
 */
@Configuration
public class EntryTokenJwtConfig {

    @Bean
    public JwtEncoder entryTokenJwtEncoder(EntryTokenProperties properties) {
        RSAPublicKey publicKey = parsePublicKey(properties.publicKeyLocation());
        RSAPrivateKey privateKey = parsePrivateKey(properties.privateKeyLocation());

        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("entryToken private key와 public key가 동일한 키쌍이 아닙니다.");
        }

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    private RSAPrivateKey parsePrivateKey(Resource resource) {
        try {
            byte[] decoded = Base64.getDecoder().decode(readKey(resource));
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
            return (RSAPrivateKey) privateKey;
        } catch (Exception exception) {
            throw new IllegalStateException("entryToken private key 설정을 확인해주세요.", exception);
        }
    }

    private RSAPublicKey parsePublicKey(Resource resource) {
        try {
            byte[] decoded = Base64.getDecoder().decode(readKey(resource));
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
            return (RSAPublicKey) publicKey;
        } catch (Exception exception) {
            throw new IllegalStateException("entryToken public key 설정을 확인해주세요.", exception);
        }
    }

    private String readKey(Resource resource) {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("entryToken 키 파일이 존재하지 않습니다.");
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
        } catch (Exception exception) {
            throw new IllegalStateException("entryToken 키 파일을 읽을 수 없습니다.", exception);
        }
    }
}
