ALTER TABLE outbox_events
    ADD COLUMN worker_id VARCHAR(100) NULL AFTER status,
    ADD COLUMN processing_started_at DATETIME(6) NULL AFTER worker_id,
    ADD COLUMN processing_deadline DATETIME(6) NULL AFTER processing_started_at;

CREATE INDEX idx_outbox_events_processing
    ON outbox_events (status, processing_deadline, id);

CREATE INDEX idx_outbox_events_cleanup
    ON outbox_events (status, published_at, id);
