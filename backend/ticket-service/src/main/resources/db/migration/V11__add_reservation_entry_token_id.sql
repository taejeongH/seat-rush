ALTER TABLE reservations
    ADD COLUMN entry_token_id VARCHAR(100) NULL AFTER hold_id;

CREATE INDEX idx_reservations_entry_token
    ON reservations (entry_token_id);
