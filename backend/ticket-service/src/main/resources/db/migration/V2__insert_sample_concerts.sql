INSERT INTO concerts (id, title, description, venue, poster_url)
VALUES (1, 'Seat Rush Live 2026', 'Seat Rush의 첫 번째 라이브 공연입니다.', '서울 올림픽 체조경기장',
        'https://example.com/posters/seat-rush-live-2026.jpg'),
       (2, 'Summer Sound Festival', '여름을 대표하는 아티스트들이 함께하는 음악 축제입니다.', '인천 문학경기장',
        'https://example.com/posters/summer-sound-festival.jpg'),
       (3, 'Midnight Orchestra', '한여름 밤에 즐기는 오케스트라 공연입니다.', '예술의전당 콘서트홀',
        'https://example.com/posters/midnight-orchestra.jpg');

INSERT INTO concert_schedules
    (id, concert_id, performance_at, booking_open_at, booking_close_at, status)
VALUES (1, 1, '2026-08-15 18:00:00', '2026-07-01 20:00:00', '2026-08-15 17:00:00', 'UPCOMING'),
       (2, 1, '2026-08-16 17:00:00', '2026-07-01 20:00:00', '2026-08-16 16:00:00', 'UPCOMING'),
       (3, 2, '2026-08-22 14:00:00', '2026-07-08 20:00:00', '2026-08-22 13:00:00', 'UPCOMING'),
       (4, 2, '2026-08-23 14:00:00', '2026-07-08 20:00:00', '2026-08-23 13:00:00', 'UPCOMING'),
       (5, 3, '2026-09-05 19:30:00', '2026-07-15 20:00:00', '2026-09-05 18:30:00', 'UPCOMING'),
       (6, 3, '2026-09-06 19:30:00', '2026-07-15 20:00:00', '2026-09-06 18:30:00', 'UPCOMING');
