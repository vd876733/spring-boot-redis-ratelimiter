package com.example.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    private static final long WINDOW_SIZE_MS = 60000; // 1 minute window
    private static final int LIMIT = 5; // 5 requests per minute

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String clientId) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / WINDOW_SIZE_MS) * WINDOW_SIZE_MS;
        long previousWindowStart = currentWindowStart - WINDOW_SIZE_MS;

        String currentKey = "ratelimit:" + clientId + ":" + currentWindowStart;
        String previousKey = "ratelimit:" + clientId + ":" + previousWindowStart;

        // Fetch previous window count
        String prevCountStr = redisTemplate.opsForValue().get(previousKey);
        long previousCount = prevCountStr != null ? Long.parseLong(prevCountStr) : 0;

        // Fetch current window count
        String currCountStr = redisTemplate.opsForValue().get(currentKey);
        long currentCount = currCountStr != null ? Long.parseLong(currCountStr) : 0;

        // Calculate the weight of the previous window
        double timePassedInCurrentWindow = now - currentWindowStart;
        double previousWindowWeight = 1.0 - (timePassedInCurrentWindow / (double) WINDOW_SIZE_MS);

        // Estimate total requests
        double estimatedRequests = (previousCount * previousWindowWeight) + currentCount;

        if (estimatedRequests >= LIMIT) {
            return false; // Rate limit exceeded
        }

        // Request is allowed, increment the counter for the current window
        redisTemplate.opsForValue().increment(currentKey);
        
        // Set TTL of 2 minutes to prevent Redis data bloat
        redisTemplate.expire(currentKey, Duration.ofMinutes(2));

        return true;
    }
}
