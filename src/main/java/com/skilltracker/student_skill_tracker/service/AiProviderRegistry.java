package com.skilltracker.student_skill_tracker.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Maps well-known API-key prefixes to fixed, reviewed OpenAI-compatible
 * endpoints. Never derives a URL from arbitrary key contents or performs
 * discovery requests against a user-supplied host.
 *
 * Add a new profile here only when the provider documents a stable key format,
 * an OpenAI-compatible Chat Completions endpoint, and a safe default model.
 */
@Component
public class AiProviderRegistry {

    public record ProviderProfile(
            String id,
            String displayName,
            String baseUrl,
            String defaultModel,
            List<String> keyPrefixes) {
    }

    private static final List<ProviderProfile> PROFILES = List.of(
            new ProviderProfile(
                    "meta",
                    "Meta Model API",
                    "https://api.meta.ai/v1",
                    "muse-spark-1.3",
                    List.of("LLM_")),
            new ProviderProfile(
                    "gemini",
                    "Google Gemini API",
                    "https://generativelanguage.googleapis.com/v1beta/openai",
                    "gemini-4-hydrogen",
                    List.of("AQ.", "AIza")),
            new ProviderProfile(
                    "openrouter",
                    "OpenRouter",
                    "https://openrouter.ai/api/v1",
                    "openrouter/auto",
                    List.of("sk-or-")),
            // Kept for existing users and for the explicit custom-URL fallback.
            new ProviderProfile(
                    "mistral",
                    "Mistral API",
                    "https://api.mistral.ai/v1",
                    "mistral-small-latest",
                    List.of()));

    public Optional<ProviderProfile> detect(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        String candidate = apiKey.trim();
        return PROFILES.stream()
                .filter(profile -> profile.keyPrefixes().stream()
                        .anyMatch(candidate::startsWith))
                .findFirst();
    }

    public Optional<ProviderProfile> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return PROFILES.stream()
                .filter(profile -> profile.id().equalsIgnoreCase(id.trim()))
                .findFirst();
    }

    public Optional<ProviderProfile> findByBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        String normalized = baseUrl.trim().toLowerCase(Locale.ROOT);
        return PROFILES.stream()
                .filter(profile -> profile.baseUrl().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public String displayName(String providerId, String baseUrl) {
        return findById(providerId)
                .or(() -> findByBaseUrl(baseUrl))
                .map(ProviderProfile::displayName)
                .orElse("Custom OpenAI-compatible gateway");
    }
}
