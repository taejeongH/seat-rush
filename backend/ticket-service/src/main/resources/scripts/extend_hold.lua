--[[
  결제 대기 시점 등에서 기존 선점(Hold) 유효시간을 연장하는 Lua 스크립트입니다.
  
  KEYS: 1부터 n번째까지는 좌석 키, n+1번째는 선점 메타데이터 키,
        이후 n개는 각 좌석이 속한 구역의 hold 인덱스 Sorted Set key
  ARGV:
    1: holdId (선점 식별 UUID)
    2: userId (사용자 ID)
    3: scheduleId (공연 회차 ID)
    4: entryTokenId (진입 토큰 식별자 JTI)
    5: ttlMillis (연장할 만료 시간 밀리초)
    6: expiresAt (연장할 만료 시각 에포크 밀리초)
    7: seatCount (선점 좌석 수)
    8이후: 좌석 ID
--]]

local seatCount = tonumber(ARGV[7])
local holdKeyIndex = seatCount + 1

-- 1. 선점 메타데이터 조회
local hold = redis.call(
    'HMGET',
    KEYS[holdKeyIndex],
    'userId',
    'scheduleId',
    'entryTokenId'
)

-- 존재하지 않거나 만료된 선점 정보인 경우
if not hold[1] or not hold[2] or not hold[3] then
    return 0
end

-- 선점 소유권(사용자, 회차, 토큰 일치 여부) 검증
if hold[1] ~= ARGV[2]
    or hold[2] ~= ARGV[3]
    or hold[3] ~= ARGV[4] then
    -- 소유권이 다르면 권한 없음(-1) 반환
    return -1
end

-- 2. 대상 좌석이 여전히 현재 holdId로 선점되어 있는지 검사
for index = 1, seatCount do
    if redis.call('GET', KEYS[index]) ~= ARGV[1] then
        -- 하나라도 다른 사람이 선점했거나 만료된 경우 연장 실패(0)
        return 0
    end
end

-- 3. 검증 통과 시 모든 좌석 키의 TTL 연장
for index = 1, seatCount do
    redis.call('PEXPIRE', KEYS[index], ARGV[5])
    local indexKey = KEYS[holdKeyIndex + index]
    if indexKey then
        redis.call('ZADD', indexKey, ARGV[6], ARGV[7 + index])
    end
end

-- 4. 선점 메타데이터 해시 내부의 expiresAt 필드 업데이트 및 TTL 연장
redis.call(
    'HSET',
    KEYS[holdKeyIndex],
    'expiresAt', ARGV[6]
)
redis.call('PEXPIRE', KEYS[holdKeyIndex], ARGV[5])

-- 성공(1) 반환
return 1
