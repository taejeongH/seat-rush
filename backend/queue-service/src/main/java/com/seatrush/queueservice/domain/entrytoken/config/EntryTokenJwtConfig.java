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
 * entryToken JWT 서명에 사용할 RSA 키와 JwtEncoder를 구성합니다.
 *
 * private key는 Queue Service가 토큰 발급에 사용하고,
 * public key는 토큰 검증이 필요한 서비스에서 동일한 키쌍 여부를 확인하는 데 사용합니다.
 */
@Configuration
public class EntryTokenJwtConfig {

    /**
     * RS256 기반 entryToken JWT encoder를 생성합니다.
     */
    @Bean
    public JwtEncoder entryTokenJwtEncoder(EntryTokenProperties properties) {
        RSAPublicKey publicKey = parsePublicKey(properties.publicKeyLocation());
        RSAPrivateKey privateKey = parsePrivateKey(properties.privateKeyLocation());

        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("entryToken 공개키와 개인키가 같은 키쌍이 아닙니다.");
        }

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * PKCS8 PEM private key를 RSAPrivateKey로 변환합니다.
     */
    private RSAPrivateKey parsePrivateKey(Resource resource) {
        try {
            byte[] decoded = Base64.getDecoder().decode(readKey(resource));
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
            return (RSAPrivateKey) privateKey;
        } catch (Exception exception) {
            throw new IllegalStateException("entryToken 개인키를 읽을 수 없습니다.", exception);
        }
    }

    /**
     * X.509 PEM public key를 RSAPublicKey로 변환합니다.
     */
    private RSAPublicKey parsePublicKey(Resource resource) {
        try {
            byte[] decoded = Base64.getDecoder().decode(readKey(resource));
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
            return (RSAPublicKey) publicKey;
        } catch (Exception exception) {
            throw new IllegalStateException("entryToken 공개키를 읽을 수 없습니다.", exception);
        }
    }

    /**
     * PEM header/footer와 공백을 제거해 Base64 본문만 추출합니다.
     */
    private String readKey(Resource resource) {
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("entryToken 키 파일을 찾을 수 없습니다.");
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
        } catch (Exception exception) {
            throw new IllegalStateException("entryToken 키 파일을 읽는 중 오류가 발생했습니다.", exception);
        }
    }
}
