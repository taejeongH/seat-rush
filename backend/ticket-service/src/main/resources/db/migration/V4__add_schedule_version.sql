ALTER TABLE concert_schedules
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
