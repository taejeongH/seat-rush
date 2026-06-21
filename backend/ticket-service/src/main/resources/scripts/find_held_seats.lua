--[[
  구역별 hold 인덱스에서 만료된 좌석을 제거하고 현재 선점 좌석 ID만 조회합니다.

  KEYS:
    1: 구역별 hold 인덱스 Sorted Set key
--]]

local redisTime = redis.call('TIME')
local nowMillis = tonumber(redisTime[1]) * 1000
        + math.floor(tonumber(redisTime[2]) / 1000)

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', nowMillis)

if redis.call('ZCARD', KEYS[1]) == 0 then
    redis.call('DEL', KEYS[1])
    return {}
end

return redis.call('ZRANGE', KEYS[1], 0, -1)
