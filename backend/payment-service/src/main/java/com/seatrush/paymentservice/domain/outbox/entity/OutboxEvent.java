package com.seatrush.paymentservice.domain.outbox.entity;

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

/**
 * Kafka로 발행해야 하는 도메인 이벤트를 DB에 임시 저장하는 Outbox 엔티티입니다.
 *
 * 비즈니스 트랜잭션은 이 테이블에 이벤트를 저장하는 것까지만 책임지고,
 * 별도 Relay가 PENDING 이벤트를 가져가 Kafka로 발행합니다.
 */
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

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

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
            String aggregateId,
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
            String aggregateId,
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
     * Relay worker가 이벤트 발행 권한을 가져갑니다.
     *
     * PROCESSING 상태가 오래 남아 있으면 이전 worker가 중간에 종료된 것으로 보고
     * lease 만료 이벤트를 다시 점유할 수 있게 합니다.
     */
    public boolean claim(
            String workerId,
            LocalDateTime now,
            LocalDateTime deadline,
            int maxRetryCount
    ) {
        if (status == OutboxStatus.PROCESSING) {
            retryCount++;
            lastError = "처리 lease가 만료되어 재점유되었습니다.";
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
     * Kafka 발행이 성공했을 때 최종 발행 완료 상태로 변경합니다.
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
     * 네트워크 오류처럼 재시도 가능한 실패를 기록합니다.
     *
     * 실패 횟수에 따라 1초, 2초, 4초처럼 재시도 간격을 지수 백오프로 늘립니다.
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
     * JSON payload 손상처럼 재시도해도 복구하기 어려운 실패를 기록합니다.
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

    /**
     * 현재 이벤트를 점유한 worker만 상태를 변경할 수 있도록 확인합니다.
     */
    private boolean isProcessingBy(String workerId) {
        return status == OutboxStatus.PROCESSING
                && this.workerId != null
                && this.workerId.equals(workerId);
    }

    /**
     * 이벤트를 최종 실패 상태로 변경하고 점유 정보를 초기화합니다.
     */
    private void markFailed(LocalDateTime now) {
        status = OutboxStatus.FAILED;
        clearProcessing();
        updatedAt = now;
    }

    /**
     * worker 점유 정보와 lease 만료 시간을 제거합니다.
     */
    private void clearProcessing() {
        workerId = null;
        processingStartedAt = null;
        processingDeadline = null;
    }

    /**
     * DB 컬럼 길이를 넘지 않도록 오류 메시지를 제한합니다.
     */
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

    public String getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }
}
