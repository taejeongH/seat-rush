--[[
  여러 좌석을 원자적으로 선점(Hold)하는 Lua 스크립트입니다.
  
  KEYS: 1부터 (n-1)번째까지는 좌석 키(예: seat:{scheduleId}:{seatId}), 마지막 n번째는 선점 메타데이터 키(예: hold:{holdId})
  ARGV:
    1: holdId (선점 식별 UUID)
    2: userId (사용자 ID)
    3: scheduleId (공연 회차 ID)
    4: entryTokenId (진입 토큰 식별자 JTI)
    5: ttlMillis (만료 시간 밀리초)
    6: seatIds (좌석 ID 목록 직렬화 문자열)
    7: expiresAt (만료 시각 에포크 밀리초)
    8: practiceSessionId (연습 세션 ID, 없을 경우 빈 문자열)
    9이후: 각 좌석의 정적 식별 ID (검증 실패 시 반환용)
--]]

-- 1. 요청된 좌석 중 이미 선점된 좌석이 있는지 확인 (EXISTS 검사)
local seatCount = #KEYS - 1
for index = 1, seatCount do
    if redis.call('EXISTS', KEYS[index]) == 1 then
        -- 이미 선점된 경우, 실패 코드(0)와 해당 좌석 식별 ID를 반환
        return {0, ARGV[8 + index]}
    end
end

-- 2. 모든 좌석이 비어있다면, 각 좌석에 선점 정보 등록 (PSETEX로 TTL 설정)
for index = 1, seatCount do
    redis.call('PSETEX', KEYS[index], ARGV[5], ARGV[1])
end

-- 3. 선점 메타데이터 해시 정보 저장
redis.call(
    'HSET',
    KEYS[#KEYS],
    'userId', ARGV[2],
    'scheduleId', ARGV[3],
    'entryTokenId', ARGV[4],
    'seatIds', ARGV[6],
    'expiresAt', ARGV[7],
    'practiceSessionId', ARGV[8]
)
-- 4. 선점 메타데이터 키에도 동일한 TTL 설정
redis.call('PEXPIRE', KEYS[#KEYS], ARGV[5])

-- 성공 코드(1) 반환
return {1, ''}
