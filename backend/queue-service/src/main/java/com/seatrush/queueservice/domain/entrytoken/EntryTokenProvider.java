package com.seatrush.queueservice.domain.entrytoken;

import com.seatrush.queueservice.domain.entrytoken.config.EntryTokenProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 좌석 선택 단계에서 사용할 entryToken JWT를 생성합니다.
 *
 * 실제 회차 토큰에는 scheduleId를, 연습 모드 토큰에는 practiceSessionId를 함께 담아
 * Ticket Service가 실제 예매와 연습 예매를 구분할 수 있게 합니다.
 */
@Component
public class EntryTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final EntryTokenProperties properties;

    public EntryTokenProvider(
            @Qualifier("entryTokenJwtEncoder") JwtEncoder jwtEncoder,
            EntryTokenProperties properties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * 실제 회차용 entryToken 후보를 생성합니다.
     */
    public EntryTokenCandidate create(Long scheduleId, Long userId) {
        return create(scheduleId, userId, null);
    }

    /**
     * 지정된 회차 또는 연습 세션용 entryToken 후보를 생성합니다.
     */
    public EntryTokenCandidate create(Long scheduleId, Long userId, String practiceSessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        String jti = UUID.randomUUID().toString();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(java.util.List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(userId.toString())
                .id(jti)
                .claim("scheduleId", scheduleId);
        if (practiceSessionId != null && !practiceSessionId.isBlank()) {
            claims.claim("practiceSessionId", practiceSessionId);
        }

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new EntryTokenCandidate(token, jti, expiresAt);
    }
}
