CREATE TABLE payments
(
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT         NOT NULL,
    user_id        BIGINT         NOT NULL,
    amount         DECIMAL(12, 0) NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    completed_at   DATETIME(6)    NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_reservation UNIQUE (reservation_id)
);

CREATE INDEX idx_payments_user_created
    ON payments (user_id, created_at);
