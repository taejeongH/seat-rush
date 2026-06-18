--[[
  entryToken과 활성 입장 슬롯을 함께 제거합니다.

  KEYS:
    1. user entryToken key
    2. active entry sorted set
  ARGV:
    1. entryToken id(jti)
--]]

redis.call('DEL', KEYS[1])
return redis.call('ZREM', KEYS[2], ARGV[1])
