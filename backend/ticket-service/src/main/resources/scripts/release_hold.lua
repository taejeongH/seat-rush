--[[
  특정 선점(Hold) 정보를 안전하게 해제(Release)하는 Lua 스크립트입니다.
  
  KEYS: 1부터 (n-1)번째까지는 좌석 키(예: seat:{scheduleId}:{seatId}), 마지막 n번째는 선점 메타데이터 키(예: hold:{holdId})
  ARGV:
    1: holdId (선점 식별 UUID)
--]]

local holdId = ARGV[1]

-- 1. 각 좌석 키에 저장된 holdId가 현재 요청된 holdId와 일치하는 경우에만 삭제 (동시성 안전 해제)
for index = 1, #KEYS - 1 do
    if redis.call('GET', KEYS[index]) == holdId then
        redis.call('DEL', KEYS[index])
    end
end

-- 2. 선점 메타데이터 키 삭제 및 결과 반환
return redis.call('DEL', KEYS[#KEYS])
