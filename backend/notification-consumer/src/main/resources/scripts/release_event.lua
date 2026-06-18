--[[
  실패한 알림 이벤트의 PROCESSING 선점을 해제합니다.

  KEYS:
    1. notification event key
--]]

if redis.call('GET', KEYS[1]) == 'PROCESSING' then
    return redis.call('DEL', KEYS[1])
end

return 0
