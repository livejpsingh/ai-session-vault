package com.jitendra.ai.sessionvault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.config.VaultFeatureFlags;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DoS Protection & Rate Limiting Filter (Gap 4).
 * Enforces per-IP burst limits when security hardening feature flag is enabled.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final VaultFeatureFlags featureFlags;
    private final ObjectMapper objectMapper;

    private static final int MAX_REQUESTS_PER_MINUTE = 300;
    private final Map<String, WindowCounter> requestCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!featureFlags.isSecurityHardening() || isExcluded(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        long currentMinute = System.currentTimeMillis() / 60000;

        WindowCounter counter = requestCounts.compute(clientIp, (ip, wc) -> {
            if (wc == null || wc.minuteWindow != currentMinute) {
                return new WindowCounter(currentMinute, new AtomicInteger(1));
            }
            wc.counter.incrementAndGet();
            return wc;
        });

        if (counter.counter.get() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for client IP {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = Map.of(
                    "timestamp", OffsetDateTime.now().toString(),
                    "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "error", "Too Many Requests",
                    "message", "Rate limit exceeded. Maximum 300 requests per minute allowed."
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcluded(String path) {
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/info");
    }

    private record WindowCounter(long minuteWindow, AtomicInteger counter) {}
}
