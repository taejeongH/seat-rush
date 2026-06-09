CREATE TABLE concerts
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    venue       VARCHAR(200) NOT NULL,
    poster_url  VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

CREATE TABLE concert_schedules
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    concert_id       BIGINT      NOT NULL,
    performance_at   DATETIME(6) NOT NULL,
    booking_open_at  DATETIME(6) NOT NULL,
    booking_close_at DATETIME(6) NOT NULL,
    status           VARCHAR(30) NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_concert_schedules_concert
        FOREIGN KEY (concert_id) REFERENCES concerts (id)
);

CREATE INDEX idx_concert_schedules_concert_performance
    ON concert_schedules (concert_id, performance_at);

CREATE INDEX idx_concert_schedules_booking_open
    ON concert_schedules (booking_open_at);
