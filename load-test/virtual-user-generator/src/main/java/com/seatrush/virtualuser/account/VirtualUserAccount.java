package com.seatrush.virtualuser.account;

import java.time.Instant;

public record VirtualUserAccount(
        int number,
        String email,
        String password,
        boolean registered,
        String accessToken,
        Instant tokenExpiresAt
) {

    public VirtualUserAccount register() {
        return new VirtualUserAccount(
                number, email, password, true, accessToken, tokenExpiresAt
        );
    }

    public VirtualUserAccount authenticate(String token, Instant expiresAt) {
        return new VirtualUserAccount(
                number, email, password, true, token, expiresAt
        );
    }

    public boolean hasUsableToken(Instant requiredUntil) {
        return accessToken != null
                && tokenExpiresAt != null
                && tokenExpiresAt.isAfter(requiredUntil);
    }
}
