package com.seatrush.ticketservice.domain.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxStatus status;

    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_deadline")
    private LocalDateTime processingDeadline;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(
            UUID eventId,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String topic,
            String payload,
            LocalDateTime now
    ) {
        this.eventId = eventId.toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static OutboxEvent create(
            UUID eventId,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String topic,
            String payload
    ) {
        return new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                payload,
                LocalDateTime.now()
        );
    }

    /**
     * 이벤트 처리 소유권과 lease 만료 시간을 기록합니다.
     */
    public boolean claim(
            String workerId,
            LocalDateTime now,
            LocalDateTime deadline,
            int maxRetryCount
    ) {
        if (status == OutboxStatus.PROCESSING) {
            retryCount++;
            lastError = "Processing lease expired";
            if (retryCount > maxRetryCount) {
                markFailed(now);
                return false;
            }
        }

        status = OutboxStatus.PROCESSING;
        this.workerId = workerId;
        processingStartedAt = now;
        processingDeadline = deadline;
        updatedAt = now;
        return true;
    }

    /**
     * 현재 Worker가 소유한 이벤트를 발행 완료 상태로 변경합니다.
     */
    public boolean markPublished(String workerId, LocalDateTime publishedAt) {
        if (!isProcessingBy(workerId)) {
            return false;
        }

        status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        lastError = null;
        clearProcessing();
        updatedAt = publishedAt;
        return true;
    }

    /**
     * 재시도 가능한 실패를 기록하고 백오프 이후 다시 처리할 수 있게 합니다.
     */
    public boolean markRetryableFailure(
            String workerId,
            String errorMessage,
            int maxRetryCount,
            LocalDateTime now
    ) {
        if (!isProcessingBy(workerId)) {
            return false;
        }

        retryCount++;
        lastError = truncate(errorMessage);
        updatedAt = now;
        clearProcessing();

        if (retryCount > maxRetryCount) {
            status = OutboxStatus.FAILED;
            return true;
        }

        status = OutboxStatus.PENDING;
        long delaySeconds = 1L << (retryCount - 1);
        nextRetryAt = now.plusSeconds(delaySeconds);
        return true;
    }

    /**
     * 재시도로 복구할 수 없는 실패를 즉시 FAILED 상태로 변경합니다.
     */
    public boolean markPermanentFailure(
            String workerId,
            String errorMessage,
            LocalDateTime now
    ) {
        if (!isProcessingBy(workerId)) {
            return false;
        }

        lastError = truncate(errorMessage);
        updatedAt = now;
        markFailed(now);
        return true;
    }

    private boolean isProcessingBy(String workerId) {
        return status == OutboxStatus.PROCESSING
                && this.workerId != null
                && this.workerId.equals(workerId);
    }

    private void markFailed(LocalDateTime now) {
        status = OutboxStatus.FAILED;
        clearProcessing();
        updatedAt = now;
    }

    private void clearProcessing() {
        workerId = null;
        processingStartedAt = null;
        processingDeadline = null;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public String getWorkerId() {
        return workerId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public LocalDateTime getProcessingDeadline() {
        return processingDeadline;
    }
}
