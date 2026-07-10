package com.example.ratelimiter.filter;

import com.example.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;

    public RateLimiterFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Extract client identifier securely considering reverse proxies
        String clientId = request.getHeader("X-Forwarded-For");
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = request.getRemoteAddr();
        } else {
            // X-Forwarded-For can contain multiple IP addresses, the first one is the client
            clientId = clientId.split(",")[0].trim();
        }

        // Check rate limit and get response data
        RateLimiterService.RateLimitResponse rateLimitResponse = rateLimiterService.checkRateLimit(clientId);

        // Append standard rate limiting headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitResponse.limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimitResponse.remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(rateLimitResponse.resetInSeconds));

        // Check if the client is rate-limited
        if (!rateLimitResponse.allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too Many Requests - Please try again later");
            return; // Halt processing
        }

        // Proceed normally down the filter chain
        filterChain.doFilter(request, response);
    }
}
