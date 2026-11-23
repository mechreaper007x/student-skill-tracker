package com.skilltracker.student_skill_tracker.service;

import java.util.Locale;

/**
 * Wire protocols supported by the configurable AI gateway.
 *
 * A gateway URL alone cannot make unrelated vendor APIs interchangeable.
 * This enum makes the wire format explicit while allowing arbitrary model IDs.
 */
public enum AiGatewayProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI;

    public static AiGatewayProtocol from(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI_COMPATIBLE;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "OPENAI", "OPENAI_COMPATIBLE", "COMPATIBLE", "CHAT_COMPLETIONS" -> OPENAI_COMPATIBLE;
            case "ANTHROPIC", "CLAUDE" -> ANTHROPIC;
            case "GEMINI", "GOOGLE", "GOOGLE_GEMINI" -> GEMINI;
            default -> throw new IllegalArgumentException(
                    "Unsupported AI protocol. Choose OPENAI_COMPATIBLE, ANTHROPIC, or GEMINI.");
        };
    }

    public String wireName() {
        return name();
    }
}
