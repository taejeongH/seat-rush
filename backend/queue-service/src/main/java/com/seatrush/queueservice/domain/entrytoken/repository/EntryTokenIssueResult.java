package com.seatrush.queueservice.domain.entrytoken.repository;

public record EntryTokenIssueResult(
        EntryTokenIssueStatus status,
        String entryToken,
        long expiresAt
) {
}
