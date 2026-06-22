--[[
  입장 가능 여부 확인과 entryToken 저장을 원자적으로 처리합니다.

  KEYS:
    1. waiting sorted set
    2. active entry sorted set
    3. user entryToken key
    4. schedule state hash
    5. user session key
    6. waiting session expiration sorted set
  ARGV:
    1. userId
    2. admission capacity
    3. entryToken
    4. entryToken ttl millis
    5. entryToken id(jti)
--]]

local redisTime = redis.call('TIME')
local nowMillis =
    tonumber(redisTime[1]) * 1000
    + math.floor(tonumber(redisTime[2]) / 1000)

local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[6], '-inf', nowMillis)
for _, expiredUserId in ipairs(expiredUsers) do
    redis.call('ZREM', KEYS[1], expiredUserId)
    redis.call('ZREM', KEYS[6], expiredUserId)
end

redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', nowMillis)

-- 이미 발급된 토큰이 살아 있으면 같은 토큰을 반환합니다.
local existingToken = redis.call('GET', KEYS[3])
if existingToken then
    local remainingTtl = redis.call('PTTL', KEYS[3])
    if remainingTtl > 0 then
        return {1, existingToken, nowMillis + remainingTtl}
    end
    redis.call('DEL', KEYS[3])
end

local schedule = redis.call(
    'HMGET',
    KEYS[4],
    'status',
    'bookingOpenAt',
    'bookingCloseAt'
)
local scheduleStatus = schedule[1]
local bookingOpenAt = tonumber(schedule[2])
local bookingCloseAt = tonumber(schedule[3])

if not scheduleStatus or not bookingOpenAt or not bookingCloseAt then
    return {-3, '', 0}
end

if scheduleStatus == 'CANCELED'
    or scheduleStatus == 'BOOKING_CLOSED'
    or nowMillis < bookingOpenAt
    or nowMillis >= bookingCloseAt then
    return {-4, '', 0}
end

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
if not rank then
    return {-1, '', 0}
end

local activeCount = redis.call('ZCARD', KEYS[2])
local availableSlots = tonumber(ARGV[2]) - activeCount
if availableSlots <= 0 or rank >= availableSlots then
    return {-2, '', 0}
end

local expiresAt = nowMillis + tonumber(ARGV[4])
redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[4])
redis.call('ZADD', KEYS[2], expiresAt, ARGV[5])
local practiceDataTtlMillis = tonumber(ARGV[6])
if practiceDataTtlMillis > 0 and redis.call('PTTL', KEYS[2]) < 0 then
    redis.call('PEXPIRE', KEYS[2], practiceDataTtlMillis)
end
redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[6], ARGV[1])
redis.call('DEL', KEYS[5])

return {0, ARGV[3], expiresAt}
