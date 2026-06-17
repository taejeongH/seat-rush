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
 * 사용자와 회차 정보를 claim에 담아 RS256 entryToken을 생성합니다.
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
     * Ticket Service가 자체 검증할 수 있는 짧은 수명의 JWT entryToken을 생성합니다.
     */
    public EntryTokenCandidate create(Long scheduleId, Long userId) {
        return create(scheduleId, userId, null);
    }

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
