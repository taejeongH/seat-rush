--[[
  알림 이벤트 처리 권한을 원자적으로 선점합니다.

  KEYS:
    1. notification event key
  ARGV:
    1. processing ttl millis

  return:
    1. CLAIMED
    2. COMPLETED
    3. PROCESSING
--]]

local current = redis.call('GET', KEYS[1])
if current == 'COMPLETED' then
    return 2
end

if current == 'PROCESSING' then
    return 3
end

redis.call('PSETEX', KEYS[1], ARGV[1], 'PROCESSING')
return 1
