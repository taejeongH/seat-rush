--[[
  결제 대기 시점 등에서 기존 선점(Hold) 유효시간을 연장하는 Lua 스크립트입니다.
  
  KEYS:
    1: 선점 메타데이터 키 (e.g. seat:hold:{holdId} or practice:{practiceSessionId}:seat:hold:{holdId})
  ARGV:
    1: holdId (선점 식별 UUID)
    2: userId (사용자 ID)
    3: scheduleId (공연 회차 ID)
    4: entryTokenId (진입 토큰 식별자 JTI)
    5: ttlMillis (연장할 만료 시간 밀리초)
    6: expiresAt (연장할 만료 시각 에포크 밀리초)
    7: practiceSessionId (대기열 토큰의 연습 세션 ID, 없을 경우 빈 문자열)
--]]

-- 1. 선점 메타데이터 조회
local hold = redis.call(
    'HMGET',
    KEYS[1],
    'userId',
    'scheduleId',
    'entryTokenId',
    'seatIds',
    'sectionIds',
    'practiceSessionId'
)

-- 존재하지 않거나 만료된 선점 정보인 경우
if not hold[1] or not hold[2] or not hold[3] then
    return {0}
end

-- 선점 소유권(사용자, 회차, 토큰 일치 여부) 검증
local holdPracticeSession = hold[6] or ""
if hold[1] ~= ARGV[2]
    or hold[2] ~= ARGV[3]
    or hold[3] ~= ARGV[4]
    or holdPracticeSession ~= ARGV[7] then
    -- 소유권이 다르면 권한 없음(-1) 반환
    return {-1}
end

local seatIdsStr = hold[4]
local sectionIdsStr = hold[5]

if not seatIdsStr or seatIdsStr == "" then
    return {0}
end

-- Helper: split string by comma
local function split(str)
    local result = {}
    for token in string.gmatch(str, "[^,]+") do
        table.insert(result, token)
    end
    return result
end

local seatIds = split(seatIdsStr)
local sectionIds = {}
if sectionIdsStr and sectionIdsStr ~= "" then
    sectionIds = split(sectionIdsStr)
end

local prefix = ""
if holdPracticeSession ~= "" then
    prefix = "practice:" .. holdPracticeSession .. ":"
end

local scheduleId = hold[2]

-- 2. 대상 좌석이 여전히 현재 holdId로 선점되어 있는지 검사
for i, seatId in ipairs(seatIds) do
    local seatKey = prefix .. "seat:hold:seat:" .. scheduleId .. ":" .. seatId
    if redis.call('GET', seatKey) ~= ARGV[1] then
        -- 하나라도 다른 사람이 선점했거나 만료된 경우 연장 실패(0)
        return {0}
    end
end

-- 3. 검증 통과 시 모든 좌석 키의 TTL 연장
for i, seatId in ipairs(seatIds) do
    local seatKey = prefix .. "seat:hold:seat:" .. scheduleId .. ":" .. seatId
    redis.call('PEXPIRE', seatKey, ARGV[5])
    
    -- 구역 인덱스 갱신 (있는 경우)
    if sectionIds[i] and sectionIds[i] ~= "" then
        local indexKey = prefix .. "seat:hold:index:" .. scheduleId .. ":" .. sectionIds[i]
        redis.call('ZADD', indexKey, ARGV[6], seatId)
    end
end

-- 4. 선점 메타데이터 해시 내부의 expiresAt 필드 업데이트 및 TTL 연장
redis.call(
    'HSET',
    KEYS[1],
    'expiresAt', ARGV[6]
)
redis.call('PEXPIRE', KEYS[1], ARGV[5])

-- 성공(1) 및 좌석/구역 정보 반환
return {1, seatIdsStr, sectionIdsStr or ""}
