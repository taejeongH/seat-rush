--[[
  대기열에 남아 있는 사용자의 세션 만료 시간을 갱신합니다.

  KEYS:
    1. waiting sorted set
    2. waiting session expiration sorted set
    3. user session key
  ARGV:
    1. userId
    2. session ttl millis
--]]

local redisTime = redis.call('TIME')
local nowMillis =
    tonumber(redisTime[1]) * 1000
    + math.floor(tonumber(redisTime[2]) / 1000)

local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', nowMillis)
for _, expiredUserId in ipairs(expiredUsers) do
    redis.call('ZREM', KEYS[1], expiredUserId)
    redis.call('ZREM', KEYS[2], expiredUserId)
end

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
if not rank then
    return 0
end

local sessionExpiresAt = nowMillis + tonumber(ARGV[2])
redis.call('PSETEX', KEYS[3], ARGV[2], '1')
redis.call('ZADD', KEYS[2], sessionExpiresAt, ARGV[1])

local practiceDataTtlMillis = tonumber(ARGV[3])
local practiceTtlRefreshIntervalMillis = tonumber(ARGV[4])
if practiceDataTtlMillis > 0 then
    local scheduleStateTtl = redis.call('PTTL', KEYS[4])
    if scheduleStateTtl > 0
        and scheduleStateTtl <= practiceDataTtlMillis - practiceTtlRefreshIntervalMillis then
        redis.call('PEXPIRE', KEYS[1], practiceDataTtlMillis)
        redis.call('PEXPIRE', KEYS[2], practiceDataTtlMillis)
        redis.call('PEXPIRE', KEYS[4], practiceDataTtlMillis)
        redis.call('PEXPIRE', KEYS[5], practiceDataTtlMillis)
        redis.call('PEXPIRE', KEYS[6], practiceDataTtlMillis)
    end
end

return 1
