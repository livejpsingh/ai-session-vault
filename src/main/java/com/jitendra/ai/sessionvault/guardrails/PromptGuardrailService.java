package com.jitendra.ai.sessionvault.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Enterprise Prompt Injection & Adversarial Attack Detection Guardrail (Gap 19).
 * Evaluates incoming queries and prompts to protect autonomous agents against jailbreaks.
 */
@Service
@Slf4j
public class PromptGuardrailService {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(?:all\\s+)?(?:previous|prior)\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(?:all\\s+)?(?:prior|previous)\\s+system\\s+prompts"),
            Pattern.compile("(?i)system\\s+prompt\\s+override"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+in\\s+developer\\s+mode"),
            Pattern.compile("(?i)DAN\\s+mode\\s+enabled")
    );

    /**
     * Inspects query content for adversarial jailbreak attempts.
     *
     * @param input the candidate query or message
     * @return true if safe, false if potential injection detected
     */
    public boolean isSafe(String input) {
        if (input == null || input.isBlank()) {
            return true;
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("Prompt guardrail triggered: pattern matched '{}'", pattern.pattern());
                return false;
            }
        }
        return true;
    }

    /**
     * Sanitizes input if injection attempt is detected.
     */
    public String sanitizeIfInjected(String input) {
        if (!isSafe(input)) {
            return "[SECURITY WARNING: Potential prompt injection detected and stripped by Vault Guardrails]";
        }
        return input;
    }
}
