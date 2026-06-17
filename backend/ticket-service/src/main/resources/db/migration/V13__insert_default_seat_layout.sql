INSERT INTO seat_layouts (id, name, venue_name, description, total_seat_count)
VALUES (1, 'Seat Rush Arena 10000', 'Seat Rush Arena',
        'Practice layout with 4 sections and 10,000 seats.', 10000);

INSERT INTO seat_layout_sections (id, layout_id, name, grade, price, sort_order)
VALUES (1, 1, 'Practice A', 'VIP', 165000, 1),
       (2, 1, 'Practice B', 'R', 143000, 2),
       (3, 1, 'Practice C', 'S', 121000, 3),
       (4, 1, 'Practice D', 'A', 99000, 4);

INSERT INTO seat_layout_seats (section_id, row_name, seat_number, sort_order)
SELECT section.id,
       CONCAT('P', FLOOR((numbers.n - 1) / 100) + 1),
       ((numbers.n - 1) % 100) + 1,
       numbers.n
FROM seat_layout_sections section
         JOIN (
    SELECT ones.n
           + tens.n * 10
           + hundreds.n * 100
           + thousands.n * 1000
           + 1 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
             CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
             CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
             CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2) thousands
) numbers
WHERE section.layout_id = 1
  AND numbers.n <= 2500;
