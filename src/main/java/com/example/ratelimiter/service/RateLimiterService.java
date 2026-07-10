package com.example.ratelimiter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimitScript;

    private static final long WINDOW_SIZE_MS = 60000; // 1 minute window

    public enum Tier {
        FREE(5),
        PREMIUM(100);

        private final int limit;

        Tier(int limit) {
            this.limit = limit;
        }

        public int getLimit() {
            return limit;
        }
    }

    public static class RateLimitResponse {
        public final boolean allowed;
        public final long remaining;
        public final long resetInSeconds;
        public final int limit;

        public RateLimitResponse(boolean allowed, long remaining, long resetInSeconds, int limit) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.resetInSeconds = resetInSeconds;
            this.limit = limit;
        }
    }

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        String luaScript = 
            "local currentKey = KEYS[1]\n" +
            "local previousKey = KEYS[2]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local weight = tonumber(ARGV[2])\n" +
            "local previousCount = tonumber(redis.call('get', previousKey) or '0')\n" +
            "local currentCount = tonumber(redis.call('get', currentKey) or '0')\n" +
            "local estimated = (previousCount * weight) + currentCount\n" +
            "local allowed = 1\n" +
            "if estimated >= limit then\n" +
            "    allowed = 0\n" +
            "else\n" +
            "    redis.call('incr', currentKey)\n" +
            "    redis.call('expire', currentKey, 120)\n" +
            "end\n" +
            "local remaining = limit - estimated\n" +
            "if allowed == 1 then\n" +
            "    remaining = remaining - 1\n" +
            "end\n" +
            "if remaining < 0 then remaining = 0 end\n" +
            "return { allowed, math.floor(remaining) }";
            
        this.rateLimitScript = new DefaultRedisScript<>(luaScript, List.class);
    }

    public RateLimitResponse checkRateLimit(String clientId, int limit) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / WINDOW_SIZE_MS) * WINDOW_SIZE_MS;
        long previousWindowStart = currentWindowStart - WINDOW_SIZE_MS;

        String currentKey = "ratelimit:" + clientId + ":" + currentWindowStart;
        String previousKey = "ratelimit:" + clientId + ":" + previousWindowStart;

        // Calculate the weight of the previous window
        double timePassedInCurrentWindow = now - currentWindowStart;
        double previousWindowWeight = 1.0 - (timePassedInCurrentWindow / (double) WINDOW_SIZE_MS);

        try {
            // Execute Lua script for atomic sliding window rate limiting
            List<Long> result = (List<Long>) redisTemplate.execute(
                    rateLimitScript,
                    List.of(currentKey, previousKey),
                    String.valueOf(limit),
                    String.valueOf(previousWindowWeight)
            );

            boolean allowed = result != null && !result.isEmpty() && result.get(0) == 1L;
            long remaining = (result != null && result.size() > 1) ? result.get(1) : 0L;
            long resetInSeconds = ((currentWindowStart + WINDOW_SIZE_MS) - now) / 1000;

            return new RateLimitResponse(allowed, remaining, resetInSeconds, limit);
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limiting, failing gracefully: {}", e.getMessage());
            // Fail open: immediately return true (allowing the request through)
            return new RateLimitResponse(true, limit, 0, limit);
        }
    }
}
