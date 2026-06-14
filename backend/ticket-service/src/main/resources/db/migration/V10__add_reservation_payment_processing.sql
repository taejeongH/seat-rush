ALTER TABLE reservations
    ADD COLUMN payment_id VARCHAR(36) NULL AFTER expires_at,
    ADD CONSTRAINT uk_reservations_payment UNIQUE (payment_id);
