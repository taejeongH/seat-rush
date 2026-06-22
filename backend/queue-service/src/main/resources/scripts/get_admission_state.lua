--[[
  내 대기 상태와 입장 가능 여부를 원자적으로 계산합니다.

  KEYS:
    1. waiting sorted set
    2. active entry sorted set
    3. waiting session expiration sorted set
  ARGV:
    1. userId
    2. admission capacity
    3. session ttl millis
--]]

local redisTime = redis.call('TIME')
local nowMillis =
    tonumber(redisTime[1]) * 1000
    + math.floor(tonumber(redisTime[2]) / 1000)

-- 대기 화면 heartbeat가 끊긴 사용자를 정리합니다.
local expiredUsers = redis.call('ZRANGEBYSCORE', KEYS[3], '-inf', nowMillis)
for _, expiredUserId in ipairs(expiredUsers) do
    redis.call('ZREM', KEYS[1], expiredUserId)
    redis.call('ZREM', KEYS[3], expiredUserId)
end

-- entryToken TTL이 끝난 활성 입장 슬롯을 정리합니다.
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', nowMillis)

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
if not rank then
    return {-1, 0, 0}
end

-- 현재 조회가 들어온 사용자는 아직 대기 화면에 있는 것으로 보고 TTL을 갱신합니다.
local totalWaiting = redis.call('ZCARD', KEYS[1])
local activeCount = redis.call('ZCARD', KEYS[2])
local availableSlots = tonumber(ARGV[2]) - activeCount
local enterable = 0

if availableSlots > 0 and rank < availableSlots then
    enterable = 1
end

return {rank + 1, totalWaiting, enterable}
