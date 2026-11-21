package com.jitendra.ai.sessionvault.guardrails;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Enterprise Secret & PII Redaction Engine (Gap 20).
 * Prevents accidental persistence of API keys, tokens, and passwords into conversation history.
 */
@Service
public class SecretRedactionService {

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            // OpenAI / Anthropic style keys
            Pattern.compile("sk-[a-zA-Z0-9_-]{20,}"),
            // Google Cloud / Gemini API keys
            Pattern.compile("AIza[0-9A-Za-z-_]{35}"),
            // GitHub tokens
            Pattern.compile("ghp_[a-zA-Z0-9]{36}"),
            Pattern.compile("github_pat_[a-zA-Z0-9_]{50,}"),
            // AWS Access Keys
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            // Bearer tokens
            Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9_\\-\\.]{25,}")
    );

    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|access[_-]?token)\\s*[:=]\\s*[\"']?([^\"'\\s]{6,})[\"']?"
    );

    /**
     * Redacts known sensitive patterns and credentials from raw conversation text.
     *
     * @param text raw input text
     * @return sanitized text with secrets replaced by [REDACTED_SECRET]
     */
    public String redactSecrets(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String sanitized = text;

        for (Pattern pattern : SECRET_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED_API_KEY]");
        }

        sanitized = KEY_VALUE_SECRET.matcher(sanitized).replaceAll("$1=[REDACTED_CREDENTIAL]");

        return sanitized;
    }
}
