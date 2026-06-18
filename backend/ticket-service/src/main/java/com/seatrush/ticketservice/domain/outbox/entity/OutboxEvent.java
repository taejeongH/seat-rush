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

/**
 * 트랜잭셔널 아웃박스 패턴(Transactional Outbox Pattern)을 구현한 데이터베이스 테이블 엔티티 클래스입니다.
 * 
 * 비즈니스 상태 변경과 이벤트 발행 데이터 저장이 하나의 로컬 트랜잭션 내에서 원자적으로 처리되도록 설계되었습니다.
 * 이후 OutboxEventRelay와 같은 스케줄러 스레드(Worker)가 미발행된 이벤트를 읽어 Kafka 브로커로 비동기 전송합니다.
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

    /**
     * 새로운 아웃박스 이벤트 객체를 초기 생성합니다. (상태는 PENDING)
     */
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
     * 특정 Worker가 이벤트를 전송하기 위해 이벤트를 잠금(Claim) 처리합니다.
     * 동시 처리 시 중복 전송을 방지하며, 데드라인을 두어 임대(Lease) 기한을 설정합니다.
     *
     * @param workerId 잠금을 획득하려는 Worker의 식별 UUID
     * @param now 현재 시각
     * @param deadline 잠금 임대 만료 만료 시각
     * @param maxRetryCount 최대 허용 재시도 횟수
     * @return 잠금 획득 및 전송 계속 가능 여부 (true인 경우 정상 점유)
     */
    public boolean claim(
            String workerId,
            LocalDateTime now,
            LocalDateTime deadline,
            int maxRetryCount
    ) {
        // 이미 PROCESSING 상태인 이벤트를 다시 Claim하는 것은 이전 시도가 시간 초과되었음을 의미
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
     * 이벤트를 Kafka 브로커로 전송 완료한 후 PUBLISHED 상태로 마킹합니다.
     *
     * @param workerId 이벤트를 처리한 Worker ID
     * @param publishedAt 발행 완료 시각
     * @return 상태 업데이트 성공 여부
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
     * 전송 중 네트워크 장애 등으로 일시적인 실패가 발생했을 때 호출됩니다.
     * 지수 백오프 간격을 적용하여 다음 재시도 예정 시각을 점증적으로 늦춥니다.
     *
     * @param workerId 이벤트를 담당했던 Worker ID
     * @param errorMessage 에러 내용
     * @param maxRetryCount 최대 재시도 한계 횟수
     * @param now 현재 시각
     * @return 상태 복구 성공 여부
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

        // 최대 재시도 횟수 초과 시 최종 실패(FAILED) 처리
        if (retryCount > maxRetryCount) {
            status = OutboxStatus.FAILED;
            return true;
        }

        status = OutboxStatus.PENDING;
        // 지수 백오프 계산 (1초, 2초, 4초, 8초 등)
        long delaySeconds = 1L << (retryCount - 1);
        nextRetryAt = now.plusSeconds(delaySeconds);
        return true;
    }

    /**
     * 페이로드 형식 오류 등 재시도해도 극복할 수 없는 에러가 발생한 경우 즉시 영구 실패(FAILED)로 분류합니다.
     *
     * @param workerId 이벤트를 담당했던 Worker ID
     * @param errorMessage 에러 내용
     * @param now 현재 시각
     * @return 상태 전환 성공 여부
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
     * 지정된 Worker가 현재 이벤트를 올바르게 독점하고 있는지 판별합니다.
     */
    private boolean isProcessingBy(String workerId) {
        return status == OutboxStatus.PROCESSING
                && this.workerId != null
                && this.workerId.equals(workerId);
    }

    /**
     * 아웃박스 이벤트를 영구 실패 상태로 바꿉니다.
     */
    private void markFailed(LocalDateTime now) {
        status = OutboxStatus.FAILED;
        clearProcessing();
        updatedAt = now;
    }

    /**
     * 작업 배분 정보를 초기화합니다.
     */
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

