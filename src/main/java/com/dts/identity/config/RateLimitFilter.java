package com.dts.identity.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter using token bucket algorithm.
 * Protects against brute-force attacks on auth endpoints.
 */
@Slf4j
@Component
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final int AUTH_MAX_REQUESTS_PER_MINUTE = 10;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        boolean isAuthEndpoint = path.contains("/api/v1/auth/");
        int maxRequests = isAuthEndpoint ? AUTH_MAX_REQUESTS_PER_MINUTE : MAX_REQUESTS_PER_MINUTE;

        String clientIp = getClientIp(httpRequest);
        String key = clientIp + ":" + (isAuthEndpoint ? "auth" : "api");

        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(maxRequests));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded: ip={}, path={}", clientIp, path);
            httpResponse.setStatus(429);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"," +
                "\"data\":null,\"errorCode\":\"RATE-429\",\"traceId\":null," +
                "\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class TokenBucket {
        private final int maxTokens;
        private volatile double tokens;
        private volatile long lastRefill;

        TokenBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = maxTokens;
            this.lastRefill = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedMinutes = (now - lastRefill) / 60_000_000_000.0;
            tokens = Math.min(maxTokens, tokens + elapsedMinutes * maxTokens);
            lastRefill = now;
        }
    }
}
