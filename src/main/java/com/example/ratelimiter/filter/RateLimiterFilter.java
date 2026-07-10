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

        // Extract client identifier
        String clientId = request.getRemoteAddr();

        // Check if the client is rate-limited
        if (!rateLimiterService.isAllowed(clientId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too Many Requests - Please try again later");
            return; // Halt processing
        }

        // Proceed normally down the filter chain
        filterChain.doFilter(request, response);
    }
}
