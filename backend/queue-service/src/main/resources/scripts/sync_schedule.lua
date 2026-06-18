--[[
  Ticket Service 회차 상태 이벤트를 Redis Hash에 동기화합니다.

  KEYS:
    1. schedule state hash
  ARGV:
    1. status
    2. bookingOpenAt epoch millis
    3. bookingCloseAt epoch millis
    4. version
--]]

local current = redis.call(
    'HMGET',
    KEYS[1],
    'version',
    'bookingOpenAt',
    'bookingCloseAt'
)
local currentVersion = current[1]

if currentVersion then
    if tonumber(currentVersion) > tonumber(ARGV[4]) then
        return 0
    end

    local timeFormatIsCurrent =
        tonumber(current[2]) ~= nil and tonumber(current[3]) ~= nil

    if tonumber(currentVersion) == tonumber(ARGV[4])
        and timeFormatIsCurrent then
        return 0
    end
end

redis.call(
    'HSET',
    KEYS[1],
    'status', ARGV[1],
    'bookingOpenAt', ARGV[2],
    'bookingCloseAt', ARGV[3],
    'version', ARGV[4]
)
return 1
