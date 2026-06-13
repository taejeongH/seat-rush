INSERT INTO seat_sections (schedule_id, name, grade, price, sort_order)
SELECT id, 'VIP', 'VIP', 150000, 1
FROM concert_schedules;

INSERT INTO seat_sections (schedule_id, name, grade, price, sort_order)
SELECT id, 'R', 'R', 110000, 2
FROM concert_schedules;

INSERT INTO seats (section_id, row_name, seat_number, status)
SELECT section.id,
       seat_row.row_name,
       seat_number.seat_number,
       'AVAILABLE'
FROM seat_sections section
         CROSS JOIN (
    SELECT 'A' AS row_name
    UNION ALL
    SELECT 'B'
) seat_row
         CROSS JOIN (
    SELECT 1 AS seat_number
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
    UNION ALL SELECT 5
    UNION ALL SELECT 6
    UNION ALL SELECT 7
    UNION ALL SELECT 8
    UNION ALL SELECT 9
    UNION ALL SELECT 10
) seat_number;
