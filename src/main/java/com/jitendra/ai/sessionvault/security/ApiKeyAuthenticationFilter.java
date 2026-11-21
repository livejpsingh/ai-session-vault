package com.jitendra.ai.sessionvault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless API Key Authentication Filter supporting Constant-Time validation
 * and a Dual-Key Overlap Window for zero-downtime key rotation (Mitigates Risk R1).
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final List<String> expectedApiKeys;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(String expectedApiKey, ObjectMapper objectMapper) {
        this(parseKeys(expectedApiKey), objectMapper);
    }

    public ApiKeyAuthenticationFilter(List<String> expectedApiKeys, ObjectMapper objectMapper) {
        this.expectedApiKeys = expectedApiKeys != null ? expectedApiKeys : Collections.emptyList();
        this.objectMapper = objectMapper;
    }

    private static List<String> parseKeys(String keyString) {
        if (keyString == null || keyString.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(keyString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Allow public endpoints
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = extractApiKey(request);

        if (apiKey != null && isValidApiKey(apiKey)) {
            ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(apiKey);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } else {
            log.warn("Unauthorized access attempt to {} from IP {}", path, request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("timestamp", OffsetDateTime.now().toString());
            errorBody.put("status", HttpStatus.UNAUTHORIZED.value());
            errorBody.put("error", "Unauthorized");
            errorBody.put("message", "Invalid or missing API key. Provide header 'X-API-KEY' or 'Authorization: Bearer <key>'.");

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        }
    }

    /**
     * Constant-time comparison across all active keys in the rotation window.
     */
    private boolean isValidApiKey(String candidate) {
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);
        for (String expected : expectedApiKeys) {
            byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(candidateBytes, expectedBytes)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/actuator/metrics") ||
               path.equals("/error") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               // MCP OAuth discovery — must be reachable before the client sends credentials
               path.startsWith("/.well-known/oauth-protected-resource");
    }

    private String extractApiKey(HttpServletRequest request) {
        // 1. Header: X-API-KEY
        String key = request.getHeader("X-API-KEY");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        // 2. Header: Authorization: Bearer <key>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 3. Query parameter: ?apiKey=... (useful for SSE EventSource connections)
        String paramKey = request.getParameter("apiKey");
        if (paramKey != null && !paramKey.isBlank()) {
            return paramKey.trim();
        }

        return null;
    }
}
