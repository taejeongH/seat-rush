package com.seatrush.ticketservice.domain.seat.repository;

import com.seatrush.ticketservice.common.metrics.BusinessMetrics;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis에 좌석 임시 선점 상태와 선점 메타데이터를 저장하고 조회합니다.
 */
@Repository
public class SeatHoldRedisRepository {

    private static final DefaultRedisScript<List> HOLD_SEATS_SCRIPT;
    private static final DefaultRedisScript<Long> RELEASE_HOLD_SCRIPT;
    private static final DefaultRedisScript<Long> EXTEND_HOLD_SCRIPT;
    private static final DefaultRedisScript<List> FIND_HELD_SEATS_SCRIPT;

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

        FIND_HELD_SEATS_SCRIPT = new DefaultRedisScript<>();
        FIND_HELD_SEATS_SCRIPT.setLocation(new ClassPathResource("scripts/find_held_seats.lua"));
        FIND_HELD_SEATS_SCRIPT.setResultType(List.class);
    }

    private final RedisTemplate<String, String> redisTemplate;
    private final BusinessMetrics businessMetrics;

    public SeatHoldRedisRepository(
            RedisTemplate<String, String> redisTemplate,
            BusinessMetrics businessMetrics
    ) {
        this.redisTemplate = redisTemplate;
        this.businessMetrics = businessMetrics;
    }


    /**
     * 요청한 모든 좌석이 사용 가능한 경우에만 원자적으로 선점합니다.
     */
    @SuppressWarnings("unchecked")
    public SeatHoldResult hold(SeatHold hold, long ttlMillis) {
        List<String> keys = holdKeys(hold);

        List<String> arguments = new ArrayList<>();
        arguments.add(hold.holdId());
        arguments.add(hold.userId().toString());
        arguments.add(hold.scheduleId().toString());
        arguments.add(hold.entryTokenId());
        arguments.add(Long.toString(ttlMillis));
        arguments.add(joinSeatIds(hold.seatIds()));
        arguments.add(joinSectionIds(hold));
        arguments.add(Long.toString(hold.expiresAt().toEpochMilli()));
        arguments.add(nullToBlank(hold.practiceSessionId()));
        arguments.add(Integer.toString(hold.seatIds().size()));
        hold.seatIds().forEach(seatId -> arguments.add(seatId.toString()));

        List<Object> result = businessMetrics.record(
                "seat.hold.redis",
                mode(hold),
                () -> redisTemplate.execute(
                        HOLD_SEATS_SCRIPT,
                        keys,
                        arguments.toArray()
                )
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
     * 실제 예매 영역에서 holdId로 좌석 선점 정보를 조회합니다.
     */
    public SeatHold findById(String holdId) {
        return findById(holdId, null);
    }

    /**
     * 연습 세션 영역을 선택적으로 지정해 holdId로 좌석 선점 정보를 조회합니다.
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
                parseSeatSectionIds(values.get("seatIds").toString(), values.get("sectionIds")),
                Instant.ofEpochMilli(Long.parseLong(values.get("expiresAt").toString()))
        );
    }

    /**
     * 예매 결제 만료 시각까지 좌석 선점의 유효 시간을 연장합니다.
     */
    public boolean extendForReservation(
            SeatHold hold,
            long ttlMillis,
            Instant expiresAt
    ) {
        List<String> keys = holdKeys(hold);

        List<String> arguments = new ArrayList<>();
        arguments.add(hold.holdId());
        arguments.add(hold.userId().toString());
        arguments.add(hold.scheduleId().toString());
        arguments.add(hold.entryTokenId());
        arguments.add(Long.toString(ttlMillis));
        arguments.add(Long.toString(expiresAt.toEpochMilli()));
        arguments.add(Integer.toString(hold.seatIds().size()));
        hold.seatIds().forEach(seatId -> arguments.add(seatId.toString()));

        Long result = redisTemplate.execute(
                EXTEND_HOLD_SCRIPT,
                keys,
                arguments.toArray()
        );

        if (result == null) {
            throw new IllegalStateException("seat hold extension result is unavailable.");
        }
        return result == 1;
    }

    /**
     * 해당 선점과 선점에 속한 모든 좌석 키를 함께 해제합니다.
     */
    public boolean release(SeatHold hold) {
        List<String> keys = holdKeys(hold);

        List<String> arguments = new ArrayList<>();
        arguments.add(hold.holdId());
        arguments.add(Integer.toString(hold.seatIds().size()));
        hold.seatIds().forEach(seatId -> arguments.add(seatId.toString()));

        Long deleted = redisTemplate.execute(
                RELEASE_HOLD_SCRIPT,
                keys,
                arguments.toArray()
        );
        return deleted != null && deleted > 0;
    }

    /**
     * 실제 예매 영역에서 여러 좌석의 선점 여부를 일괄 조회합니다.
     */
    public Map<Long, Boolean> findHeldSeats(Long scheduleId, Long sectionId, List<Long> seatIds) {
        return findHeldSeats(scheduleId, sectionId, seatIds, null);
    }

    /**
     * 연습 세션 영역을 선택적으로 지정해 여러 좌석의 선점 여부를 일괄 조회합니다.
     */
    public Map<Long, Boolean> findHeldSeats(
            Long scheduleId,
            Long sectionId,
            List<Long> seatIds,
            String practiceSessionId
    ) {
        if (seatIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String mode = practiceSessionId == null ? "real" : "practice";
        String indexKey = SeatHoldKey.sectionIndex(scheduleId, sectionId, practiceSessionId);
        List<Object> values = businessMetrics.record(
                "seat.query.hold.redis.index",
                mode,
                () -> redisTemplate.execute(FIND_HELD_SEATS_SCRIPT, List.of(indexKey))
        );
        Set<Long> heldSeatIds = new HashSet<>();
        if (values != null) {
            values.forEach(value -> heldSeatIds.add(toLong(value)));
        }

        return businessMetrics.record("seat.query.hold.mapping", mode, () -> {
            Map<Long, Boolean> result = new HashMap<>();
            seatIds.forEach(seatId -> result.put(seatId, heldSeatIds.contains(seatId)));
            return result;
        });
    }

    private List<String> holdKeys(SeatHold hold) {
        List<String> keys = hold.seatIds().stream()
                .map(seatId -> SeatHoldKey.seat(
                        hold.scheduleId(),
                        seatId,
                        hold.practiceSessionId()
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        keys.add(SeatHoldKey.hold(hold.holdId(), hold.practiceSessionId()));
        if (hold.hasSeatSectionIds()) {
            hold.seatIds().forEach(seatId -> keys.add(SeatHoldKey.sectionIndex(
                    hold.scheduleId(),
                    hold.seatSectionIds().get(seatId),
                    hold.practiceSessionId()
            )));
        }
        return keys;
    }

    private String joinSeatIds(List<Long> seatIds) {
        return String.join(",", seatIds.stream().map(String::valueOf).toList());
    }

    private String joinSectionIds(SeatHold hold) {
        if (!hold.hasSeatSectionIds()) {
            return "";
        }
        return String.join(",", hold.seatIds().stream()
                .map(hold.seatSectionIds()::get)
                .map(String::valueOf)
                .toList());
    }

    private List<Long> parseSeatIds(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(Long::valueOf)
                .toList();
    }

    private Map<Long, Long> parseSeatSectionIds(String seatIds, Object sectionIds) {
        if (sectionIds == null || sectionIds.toString().isBlank()) {
            return Map.of();
        }

        List<Long> parsedSeatIds = parseSeatIds(seatIds);
        List<Long> parsedSectionIds = parseSeatIds(sectionIds.toString());
        if (parsedSeatIds.size() != parsedSectionIds.size()) {
            return Map.of();
        }

        Map<Long, Long> result = new HashMap<>();
        for (int index = 0; index < parsedSeatIds.size(); index++) {
            result.put(parsedSeatIds.get(index), parsedSectionIds.get(index));
        }
        return result;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String mode(SeatHold hold) {
        return hold.practiceSessionId() == null ? "real" : "practice";
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
