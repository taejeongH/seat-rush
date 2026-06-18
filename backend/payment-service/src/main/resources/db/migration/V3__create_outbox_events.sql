CREATE TABLE outbox_events
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    event_id              VARCHAR(36)   NOT NULL,
    aggregate_type        VARCHAR(100)  NOT NULL,
    aggregate_id          VARCHAR(100)  NOT NULL,
    event_type            VARCHAR(100)  NOT NULL,
    topic                 VARCHAR(200)  NOT NULL,
    payload               TEXT          NOT NULL,
    status                VARCHAR(30)   NOT NULL,
    worker_id             VARCHAR(100)  NULL,
    processing_started_at DATETIME(6)   NULL,
    processing_deadline   DATETIME(6)   NULL,
    retry_count           INT           NOT NULL DEFAULT 0,
    next_retry_at         DATETIME(6)   NOT NULL,
    published_at          DATETIME(6)   NULL,
    last_error            VARCHAR(1000) NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_event_id UNIQUE (event_id)
);

CREATE INDEX idx_outbox_events_publish
    ON outbox_events (status, next_retry_at, id);

CREATE INDEX idx_outbox_events_processing
    ON outbox_events (status, processing_deadline, id);
