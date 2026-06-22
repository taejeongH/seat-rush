--[[
  대기열 진입을 원자적으로 처리합니다.

  KEYS:
    1. waiting sorted set
    2. sequence key
    3. schedule state hash
    4. waiting session expiration sorted set
    5. user session key
  ARGV:
    1. userId
    2. session ttl millis
--]]

local redisTime = redis.call('TIME')
local nowMillis =
    tonumber(redisTime[1]) * 1000
    + math.floor(tonumber(redisTime[2]) / 1000)

-- heartbeat가 끊겨 만료된 사용자를 대기열에서 제거합니다.
local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[4], '-inf', nowMillis)
for _, expiredUserId in ipairs(expiredUsers) do
    redis.call('ZREM', KEYS[1], expiredUserId)
    redis.call('ZREM', KEYS[4], expiredUserId)
end

local schedule = redis.call(
    'HMGET',
    KEYS[3],
    'status',
    'bookingOpenAt',
    'bookingCloseAt'
)
local scheduleStatus = schedule[1]
local bookingOpenAt = tonumber(schedule[2])
local bookingCloseAt = tonumber(schedule[3])

if not scheduleStatus or not bookingOpenAt or not bookingCloseAt then
    return {-1, 0}
end

if scheduleStatus == 'CANCELED'
    or scheduleStatus == 'BOOKING_CLOSED'
    or nowMillis < bookingOpenAt
    or nowMillis >= bookingCloseAt then
    return {-2, 0}
end

local sessionExpiresAt = nowMillis + tonumber(ARGV[2])
local practiceDataTtlMillis = tonumber(ARGV[3])

local function expirePracticeKeyIfNeeded(key)
    if practiceDataTtlMillis > 0 and redis.call('PTTL', key) < 0 then
        redis.call('PEXPIRE', key, practiceDataTtlMillis)
    end
end

local function expirePracticeQueueKeys()
    expirePracticeKeyIfNeeded(KEYS[1])
    expirePracticeKeyIfNeeded(KEYS[2])
    expirePracticeKeyIfNeeded(KEYS[4])
end

-- 이미 진입한 사용자는 기존 순번을 반환하고 세션 TTL만 갱신합니다.
if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
    redis.call('PSETEX', KEYS[5], ARGV[2], '1')
    redis.call('ZADD', KEYS[4], sessionExpiresAt, ARGV[1])
    expirePracticeQueueKeys()
    local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
    return {rank + 1, 1}
end

local sequence = redis.call('INCR', KEYS[2])
redis.call('ZADD', KEYS[1], sequence, ARGV[1])
redis.call('PSETEX', KEYS[5], ARGV[2], '1')
redis.call('ZADD', KEYS[4], sessionExpiresAt, ARGV[1])
expirePracticeQueueKeys()

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
return {rank + 1, 0}
