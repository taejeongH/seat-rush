--[[
  특정 선점(Hold) 정보를 안전하게 해제(Release)하는 Lua 스크립트입니다.
  
  KEYS: 1부터 n번째까지는 좌석 키, n+1번째는 선점 메타데이터 키,
        이후 n개는 각 좌석이 속한 구역의 hold 인덱스 Sorted Set key
  ARGV:
    1: holdId (선점 식별 UUID)
    2: seatCount (선점 좌석 수)
    3이후: 좌석 ID
--]]

local holdId = ARGV[1]
local seatCount = tonumber(ARGV[2])
local holdKeyIndex = seatCount + 1

-- 1. 각 좌석 키에 저장된 holdId가 현재 요청된 holdId와 일치하는 경우에만 삭제 (동시성 안전 해제)
for index = 1, seatCount do
    if redis.call('GET', KEYS[index]) == holdId then
        redis.call('DEL', KEYS[index])
        local indexKey = KEYS[holdKeyIndex + index]
        if indexKey then
            redis.call('ZREM', indexKey, ARGV[2 + index])
        end
    end
end

-- 2. 선점 메타데이터 키 삭제 및 결과 반환
return redis.call('DEL', KEYS[holdKeyIndex])
