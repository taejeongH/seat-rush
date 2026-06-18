package com.seatrush.ticketservice.domain.seat.repository;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores seat hold state and hold metadata in Redis.
 */
@Repository
public class SeatHoldRedisRepository {

    private static final DefaultRedisScript<List> HOLD_SEATS_SCRIPT;
    private static final DefaultRedisScript<Long> RELEASE_HOLD_SCRIPT;
    private static final DefaultRedisScript<Long> EXTEND_HOLD_SCRIPT;

    static {
        HOLD_SEATS_SCRIPT = new DefaultRedisScript<>();
        HOLD_SEATS_SCRIPT.setLocation(new ClassPathResource("scripts/hold_seats.lua"));
        HOLD_SEATS_SCRIPT.setResultType(List.class);

        RELEASE_HOLD_SCRIPT = new DefaultRedisScript<>();
        RELEASE_HOLD_SCRIPT.setLocation(new ClassPathResource("scripts/release_hold.lua"));
        RELEASE_HOLD_SCRIPT.setResultType(Long.class);

        EXTEND_HOLD_SCRIPT = new DefaultRedisScript<>();
        EXTEND_HOLD_SCRIPT.setLocation(new ClassPathResource("scripts/extend_hold.lua"));
        EXTEND_HOLD_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, String> redisTemplate;

    public SeatHoldRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    /**
     * Atomically holds all requested seats only if every seat is available.
     */
    @SuppressWarnings("unchecked")
    public SeatHoldResult hold(SeatHold hold, long ttlMillis) {
        List<String> keys = hold.seatIds().stream()
                .map(seatId -> SeatHoldKey.seat(
                        hold.scheduleId(),
                        seatId,
                        hold.practiceSessionId()
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        keys.add(SeatHoldKey.hold(hold.holdId(), hold.practiceSessionId()));

        List<String> arguments = new ArrayList<>();
        arguments.add(hold.holdId());
        arguments.add(hold.userId().toString());
        arguments.add(hold.scheduleId().toString());
        arguments.add(hold.entryTokenId());
        arguments.add(Long.toString(ttlMillis));
        arguments.add(joinSeatIds(hold.seatIds()));
        arguments.add(Long.toString(hold.expiresAt().toEpochMilli()));
        arguments.add(nullToBlank(hold.practiceSessionId()));
        hold.seatIds().forEach(seatId -> arguments.add(seatId.toString()));

        List<Object> result = redisTemplate.execute(
                HOLD_SEATS_SCRIPT,
                keys,
                arguments.toArray()
        );

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("seat hold result is unavailable.");
        }

        if (toLong(result.getFirst()) == 1) {
            return SeatHoldResult.held();
        }
        return SeatHoldResult.unavailable(toLong(result.get(1)));
    }

    /**
     * Finds a production hold by id.
     */
    public SeatHold findById(String holdId) {
        return findById(holdId, null);
    }

    /**
     * Finds a hold by id within the optional practice namespace.
     */
    public SeatHold findById(String holdId, String practiceSessionId) {
        Map<Object, Object> values = redisTemplate.opsForHash()
                .entries(SeatHoldKey.hold(holdId, practiceSessionId));
        if (values.isEmpty()) {
            return null;
        }

        return new SeatHold(
                holdId,
                Long.valueOf(values.get("scheduleId").toString()),
                Long.valueOf(values.get("userId").toString()),
                values.get("entryTokenId").toString(),
                blankToNull(values.get("practiceSessionId")),
                parseSeatIds(values.get("seatIds").toString()),
                Instant.ofEpochMilli(Long.parseLong(values.get("expiresAt").toString()))
        );
    }

    /**
     * Extends a hold until the reservation payment deadline.
     */
    public boolean extendForReservation(
            SeatHold hold,
            long ttlMillis,
            Instant expiresAt
    ) {
        List<String> keys = hold.seatIds().stream()
                .map(seatId -> SeatHoldKey.seat(
                        hold.scheduleId(),
                        seatId,
                        hold.practiceSessionId()
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        keys.add(SeatHoldKey.hold(hold.holdId(), hold.practiceSessionId()));

        Long result = redisTemplate.execute(
                EXTEND_HOLD_SCRIPT,
                keys,
                hold.holdId(),
                hold.userId().toString(),
                hold.scheduleId().toString(),
                hold.entryTokenId(),
                Long.toString(ttlMillis),
                Long.toString(expiresAt.toEpochMilli())
        );

        if (result == null) {
            throw new IllegalStateException("seat hold extension result is unavailable.");
        }
        return result == 1;
    }

    /**
     * Releases a hold and every seat key owned by that hold.
     */
    public boolean release(SeatHold hold) {
        List<String> keys = hold.seatIds().stream()
                .map(seatId -> SeatHoldKey.seat(
                        hold.scheduleId(),
                        seatId,
                        hold.practiceSessionId()
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        keys.add(SeatHoldKey.hold(hold.holdId(), hold.practiceSessionId()));

        Long deleted = redisTemplate.execute(
                RELEASE_HOLD_SCRIPT,
                keys,
                hold.holdId()
        );
        return deleted != null && deleted > 0;
    }

    /**
     * Checks held seats in production namespace.
     */
    public Map<Long, Boolean> findHeldSeats(Long scheduleId, List<Long> seatIds) {
        return findHeldSeats(scheduleId, seatIds, null);
    }

    /**
     * Checks held seats in the optional practice namespace.
     */
    public Map<Long, Boolean> findHeldSeats(
            Long scheduleId,
            List<Long> seatIds,
            String practiceSessionId
    ) {
        if (seatIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = seatIds.stream()
                .map(seatId -> SeatHoldKey.seat(scheduleId, seatId, practiceSessionId))
                .toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, Boolean> result = new HashMap<>();
        for (int index = 0; index < seatIds.size(); index++) {
            result.put(
                    seatIds.get(index),
                    values != null && values.get(index) != null
            );
        }
        return result;
    }

    private String joinSeatIds(List<Long> seatIds) {
        return String.join(",", seatIds.stream().map(String::valueOf).toList());
    }

    private List<Long> parseSeatIds(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(Long::valueOf)
                .toList();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
