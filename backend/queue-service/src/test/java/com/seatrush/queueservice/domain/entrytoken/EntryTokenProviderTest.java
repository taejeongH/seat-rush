package com.seatrush.queueservice.domain.entrytoken;

import com.seatrush.queueservice.domain.entrytoken.config.EntryTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class EntryTokenProviderTest {

    @Autowired
    private EntryTokenProvider entryTokenProvider;

    @Autowired
    private EntryTokenProperties properties;

    /**
     * 발급한 entryToken이 사용자, 회차, 발급자, 대상 서비스 정보를 포함하는지 확인합니다.
     */
    @Test
    void entryTokenContainsRequiredClaims() throws Exception {
        EntryTokenCandidate candidate = entryTokenProvider.create(10L, 20L);
        var decoder = NimbusJwtDecoder.withPublicKey(readPublicKey()).build();
        var jwt = decoder.decode(candidate.token());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo(properties.issuer());
        assertThat(jwt.getAudience()).contains(properties.audience());
        assertThat(jwt.getSubject()).isEqualTo("20");
        assertThat((Number) jwt.getClaim("scheduleId")).extracting(Number::longValue).isEqualTo(10L);
        assertThat(jwt.getId()).isEqualTo(candidate.jti());
        assertThat(jwt.getExpiresAt()).isCloseTo(candidate.expiresAt(), within(1, ChronoUnit.SECONDS));
    }

    private RSAPublicKey readPublicKey() throws Exception {
        try (InputStream inputStream = properties.publicKeyLocation().getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(content);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
        }
    }
}
