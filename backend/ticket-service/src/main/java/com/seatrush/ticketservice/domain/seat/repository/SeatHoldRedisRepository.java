package com.seatrush.ticketservice.domain.seat.repository;

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
 * 좌석 선점 상태와 hold 메타데이터를 Redis에 저장하고 조회합니다.
 */
@Repository
public class SeatHoldRedisRepository {

    private static final DefaultRedisScript<List> HOLD_SEATS_SCRIPT =
            new DefaultRedisScript<>("""
                    -- KEYS[1..N]: 좌석별 선점 키
                    -- KEYS[N+1]: hold 상세 정보 키
                    -- ARGV[1]: holdId
                    -- ARGV[2]: userId
                    -- ARGV[3]: scheduleId
                    -- ARGV[4]: entryTokenId
                    -- ARGV[5]: TTL(ms)
                    -- ARGV[6]: 쉼표로 연결한 전체 seatIds
                    -- ARGV[7]: 만료 시각(epoch millis)
                    -- ARGV[8..]: KEYS[1..N]과 같은 순서의 개별 seatId

                    local seatCount = #KEYS - 1
                    for index = 1, seatCount do
                        if redis.call('EXISTS', KEYS[index]) == 1 then
                            -- 현재 좌석의 ID는 ARGV[7 + index]에 있습니다.
                            return {0, ARGV[7 + index]}
                        end
                    end

                    for index = 1, seatCount do
                        redis.call('PSETEX', KEYS[index], ARGV[5], ARGV[1])
                    end

                    redis.call(
                        'HSET',
                        KEYS[#KEYS],
                        'userId', ARGV[2],
                        'scheduleId', ARGV[3],
                        'entryTokenId', ARGV[4],
                        'seatIds', ARGV[6],
                        'expiresAt', ARGV[7]
                    )
                    redis.call('PEXPIRE', KEYS[#KEYS], ARGV[5])
                    return {1, ''}
                    """, List.class);

    private static final DefaultRedisScript<Long> RELEASE_HOLD_SCRIPT =
            new DefaultRedisScript<>("""
                    -- KEYS[1..N]: 좌석별 선점 키
                    -- KEYS[N+1]: hold 상세 정보 키
                    -- ARGV[1]: 해제할 holdId

                    local holdId = ARGV[1]
                    for index = 1, #KEYS - 1 do
                        if redis.call('GET', KEYS[index]) == holdId then
                            redis.call('DEL', KEYS[index])
                        end
                    end
                    return redis.call('DEL', KEYS[#KEYS])
                    """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public SeatHoldRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 모든 좌석이 비어 있을 때만 좌석 키와 hold 정보를 한 번에 저장합니다.
     */
    @SuppressWarnings("unchecked")
    public SeatHoldResult hold(SeatHold hold, long ttlMillis) {
        // KEYS[1..N]은 좌석별 선점 키이고 마지막 키는 hold 상세 정보 키입니다.
        List<String> keys = hold.seatIds().stream()
                .map(seatId -> SeatHoldKey.seat(hold.scheduleId(), seatId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        keys.add(SeatHoldKey.hold(hold.holdId()));

        // ARGV[1..7]은 hold 공통 정보이고 ARGV[8..]은 좌석 키와 같은 순서의 seatId입니다.
        List<String> arguments = new ArrayList<>();
        arguments.add(hold.holdId());                                  // ARGV[1]
        arguments.add(hold.userId().toString());                       // ARGV[2]
        arguments.add(hold.scheduleId().toString());                   // ARGV[3]
        arguments.add(hold.entryTokenId());                            // ARGV[4]
        arguments.add(Long.toString(ttlMillis));                       // ARGV[5]
        arguments.add(joinSeatIds(hold.seatIds()));                    // ARGV[6]
        arguments.add(Long.toString(hold.expiresAt().toEpochMilli())); // ARGV[7]
        hold.seatIds().forEach(
                seatId -> arguments.add(seatId.toString())             // ARGV[8..]
        );

        List<Object> result = redisTemplate.execute(
                HOLD_SEATS_SCRIPT,
                keys,
                arguments.toArray()
        );

        if (result == null || result.size() != 2) {
            throw new IllegalStateException("좌석 선점 결과를 확인할 수 없습니다.");
        }

        if (toLong(result.getFirst()) == 1) {
            return SeatHoldResult.held();
        }
        return SeatHoldResult.unavailable(toLong(result.get(1)));
    }

    /**
     * holdId에 해당하는 유효한 좌석 선점 정보를 조회합니다.
     */
    public SeatHold findById(String holdId) {
        Map<Object, Object> values = redisTemplate.opsForHash()
                .entries(SeatHoldKey.hold(holdId));
        if (values.isEmpty()) {
            return null;
        }

        return new SeatHold(
                holdId,
                Long.valueOf(values.get("scheduleId").toString()),
                Long.valueOf(values.get("userId").toString()),
                values.get("entryTokenId").toString(),
                parseSeatIds(values.get("seatIds").toString()),
                Instant.ofEpochMilli(Long.parseLong(values.get("expiresAt").toString()))
        );
    }

    /**
     * hold에 속한 좌석 키가 현재 holdId를 가리킬 때만 선점을 해제합니다.
     */
    public boolean release(SeatHold hold) {
        List<String> keys = hold.seatIds().stream()
                .map(seatId -> SeatHoldKey.seat(hold.scheduleId(), seatId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        keys.add(SeatHoldKey.hold(hold.holdId()));

        Long deleted = redisTemplate.execute(
                RELEASE_HOLD_SCRIPT,
                keys,
                hold.holdId()
        );
        return deleted != null && deleted > 0;
    }

    /**
     * 좌석별 활성 선점 여부를 한 번에 조회합니다.
     */
    public Map<Long, Boolean> findHeldSeats(Long scheduleId, List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> keys = seatIds.stream()
                .map(seatId -> SeatHoldKey.seat(scheduleId, seatId))
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

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
