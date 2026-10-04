-- Token bucket rate limiter
-- KEYS[1] = bucket key (e.g., ratelimit:api_key:123)
-- ARGV[1] = max capacity / rate per minute (e.g., 60)
-- ARGV[2] = current timestamp in seconds
-- ARGV[3] = requested tokens (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

-- Fill rate is capacity per minute
local fill_rate = capacity / 60.0

-- Get current bucket state
local bucket = redis.call('HMGET', key, 'tokens', 'last_update')
local tokens = tonumber(bucket[1])
local last_update = tonumber(bucket[2])

if tokens == nil then
    -- First time seeing this key, bucket is full
    tokens = capacity
    last_update = now
else
    -- Calculate how many tokens to add based on elapsed time
    local elapsed = math.max(0, now - last_update)
    local add_tokens = elapsed * fill_rate
    tokens = math.min(capacity, tokens + add_tokens)
end

local allowed = 0
local retry_after = 0

if tokens >= requested then
    -- Allow request, deduct tokens
    tokens = tokens - requested
    last_update = now
    allowed = 1
else
    -- Reject request, calculate retry-after in seconds
    local deficit = requested - tokens
    retry_after = math.ceil(deficit / fill_rate)
end

-- Save updated state, expire in 2 minutes to clean up unused keys
redis.call('HMSET', key, 'tokens', tokens, 'last_update', last_update)
redis.call('EXPIRE', key, 120)

return {allowed, retry_after}
