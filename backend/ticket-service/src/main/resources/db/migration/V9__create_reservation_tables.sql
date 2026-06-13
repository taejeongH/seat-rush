CREATE TABLE reservations
(
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    user_id      BIGINT         NOT NULL,
    schedule_id  BIGINT         NOT NULL,
    hold_id      VARCHAR(100)   NOT NULL,
    status       VARCHAR(30)    NOT NULL,
    total_amount DECIMAL(12, 0) NOT NULL,
    expires_at   DATETIME(6)    NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules (id),
    CONSTRAINT uk_reservations_hold
        UNIQUE (hold_id)
);

CREATE INDEX idx_reservations_user_created
    ON reservations (user_id, created_at);

CREATE INDEX idx_reservations_status_expires
    ON reservations (status, expires_at);

CREATE TABLE reservation_seats
(
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT         NOT NULL,
    seat_id        BIGINT         NOT NULL,
    price          DECIMAL(12, 0) NOT NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_seats_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_reservation_seats_seat
        FOREIGN KEY (seat_id) REFERENCES seats (id),
    CONSTRAINT uk_reservation_seats_reservation_seat
        UNIQUE (reservation_id, seat_id)
);

CREATE INDEX idx_reservation_seats_seat
    ON reservation_seats (seat_id);
