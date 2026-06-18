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
 * RSA PEM 키 파일을 읽어 토큰 발급용 `JwtEncoder`와 토큰 검증용 `JwtDecoder`를 설정하는 구성 클래스입니다.
 * 
 * - Private Key: JWT 서명에 사용하며, 보안을 위해 티켓 서비스 내부에서만 관리합니다.
 * - Public Key: JWT 서명 검증에 사용하며, 다른 다운스트림 서비스(예: API Gateway)에서도 검증에 활용할 수 있도록 배포됩니다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /**
     * RSA Private Key를 주입받아 비대칭 암호화 서명(RS256)을 처리하는 JwtEncoder 빈을 구성합니다.
     * 로딩 시 퍼블릭 키와 프라이빗 키가 일치하는 한 쌍의 키스펙인지 유효성 검사를 수행합니다.
     *
     * @param properties JWT 설정 프로퍼티
     * @return NimbusJwtEncoder 인스턴스
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        RSAPublicKey publicKey = parsePublicKey(properties.publicKeyLocation());
        RSAPrivateKey privateKey = parsePrivateKey(properties.privateKeyLocation());

        // 공개키와 개인키가 올바르게 매칭되는지 확인 (Modulus 비교)
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
     * RSA Public Key를 이용하여 JWT의 서명 및 속성값(만료기간, 발급처 등)을 검증하는 JwtDecoder 빈을 구성합니다.
     *
     * @param properties JWT 설정 프로퍼티
     * @return NimbusJwtDecoder 인스턴스
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
     * 지정된 경로의 PKCS#8 규격 PEM 파일을 파싱하여 RSAPrivateKey 인스턴스로 반환합니다.
     *
     * @param keyResource 개인키 파일 리소스
     * @return RSAPrivateKey 객체
     * @throws IllegalStateException 파싱 실패 시
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
     * 지정된 경로의 X.509 규격 PEM 파일을 파싱하여 RSAPublicKey 인스턴스로 반환합니다.
     *
     * @param keyResource 공개키 파일 리소스
     * @return RSAPublicKey 객체
     * @throws IllegalStateException 파싱 실패 시
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
     * 클래스패스에서 PEM 형식 키 파일을 읽어 헤더, 푸터 및 줄바꿈 문자를 제외하고 순수 Base64 본문 내용만 추출합니다.
     *
     * @param keyResource PEM 파일 리소스
     * @return 줄바꿈 및 헤더가 제거된 Base64 문자열
     * @throws IllegalStateException 파일이 없거나 읽을 수 없는 경우
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

