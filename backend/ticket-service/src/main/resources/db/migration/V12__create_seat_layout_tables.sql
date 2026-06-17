CREATE TABLE seat_layouts
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    venue_name       VARCHAR(200) NOT NULL,
    description      TEXT,
    total_seat_count INT NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_seat_layouts_name UNIQUE (name)
);

CREATE TABLE seat_layout_sections
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    layout_id  BIGINT NOT NULL,
    name       VARCHAR(100) NOT NULL,
    grade      VARCHAR(50) NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_seat_layout_sections_layout
        FOREIGN KEY (layout_id) REFERENCES seat_layouts (id),
    CONSTRAINT uk_seat_layout_sections_layout_name
        UNIQUE (layout_id, name)
);

CREATE INDEX idx_seat_layout_sections_layout_sort
    ON seat_layout_sections (layout_id, sort_order);

CREATE TABLE seat_layout_seats
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    section_id BIGINT NOT NULL,
    row_name   VARCHAR(20) NOT NULL,
    seat_number INT NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_seat_layout_seats_section
        FOREIGN KEY (section_id) REFERENCES seat_layout_sections (id),
    CONSTRAINT uk_seat_layout_seats_section_position
        UNIQUE (section_id, row_name, seat_number)
);

CREATE INDEX idx_seat_layout_seats_section_sort
    ON seat_layout_seats (section_id, sort_order);
