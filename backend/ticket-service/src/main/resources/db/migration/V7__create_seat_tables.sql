CREATE TABLE seat_sections
(
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT         NOT NULL,
    name        VARCHAR(100)   NOT NULL,
    grade       VARCHAR(30)    NOT NULL,
    price       DECIMAL(12, 0) NOT NULL,
    sort_order  INT            NOT NULL,
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_seat_sections_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules (id),
    CONSTRAINT uk_seat_sections_schedule_name
        UNIQUE (schedule_id, name)
);

CREATE INDEX idx_seat_sections_schedule_sort
    ON seat_sections (schedule_id, sort_order);

CREATE TABLE seats
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    section_id  BIGINT       NOT NULL,
    row_name    VARCHAR(20)  NOT NULL,
    seat_number INT          NOT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_seats_section
        FOREIGN KEY (section_id) REFERENCES seat_sections (id),
    CONSTRAINT uk_seats_section_position
        UNIQUE (section_id, row_name, seat_number)
);

CREATE INDEX idx_seats_section_status_position
    ON seats (section_id, status, row_name, seat_number);
