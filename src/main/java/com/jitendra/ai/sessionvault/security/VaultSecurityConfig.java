package com.jitendra.ai.sessionvault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class VaultSecurityConfig {

    @Value("${vault.security.api-key}")
    private String apiKey;

    @Value("${vault.security.secondary-api-key:}")
    private String secondaryApiKey;

    private final ObjectMapper objectMapper;
    private final RateLimitingFilter rateLimitingFilter;

    public VaultSecurityConfig(ObjectMapper objectMapper, RateLimitingFilter rateLimitingFilter) {
        this.objectMapper = objectMapper;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> validKeys = new ArrayList<>();
        if (apiKey != null && !apiKey.isBlank()) {
            validKeys.add(apiKey.trim());
        }
        if (secondaryApiKey != null && !secondaryApiKey.isBlank()) {
            validKeys.add(secondaryApiKey.trim());
        }

        ApiKeyAuthenticationFilter apiKeyFilter = new ApiKeyAuthenticationFilter(validKeys, objectMapper);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/metrics/**", "/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // MCP OAuth discovery endpoints — must be publicly reachable so MCP clients
                // (Antigravity, GitHub Copilot) can discover the auth scheme before sending credentials
                .requestMatchers("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }
}
