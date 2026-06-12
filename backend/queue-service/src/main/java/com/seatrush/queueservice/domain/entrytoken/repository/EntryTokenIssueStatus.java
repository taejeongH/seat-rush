package com.seatrush.queueservice.domain.entrytoken.repository;

public enum EntryTokenIssueStatus {
    ISSUED,
    ALREADY_ISSUED,
    QUEUE_ENTRY_NOT_FOUND,
    ENTRY_NOT_ALLOWED,
    SCHEDULE_NOT_FOUND,
    QUEUE_NOT_OPEN
}
