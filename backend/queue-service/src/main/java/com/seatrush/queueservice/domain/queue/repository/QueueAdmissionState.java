package com.seatrush.queueservice.domain.queue.repository;

public record QueueAdmissionState(
        long position,
        long totalWaiting,
        boolean enterable
) {
}
