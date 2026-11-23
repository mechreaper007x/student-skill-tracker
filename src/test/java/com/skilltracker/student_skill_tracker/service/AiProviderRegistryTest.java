package com.skilltracker.student_skill_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiProviderRegistryTest {

    private final AiProviderRegistry registry = new AiProviderRegistry();

    @Test
    void detectsMetaModelApiFromDocumentedLlmPrefix() {
        var profile = registry.detect("LLM_123456789_secret").orElseThrow();

        assertEquals("meta", profile.id());
        assertEquals("https://api.meta.ai/v1", profile.baseUrl());
        assertEquals("muse-spark-1.3", profile.defaultModel());
    }

    @Test
    void detectsCurrentAndLegacyGeminiApiKeyPrefixes() {
        var authKey = registry.detect("AQ.example-auth-key").orElseThrow();
        var standardKey = registry.detect("AIzaSyExampleStandardKey").orElseThrow();

        assertEquals("gemini", authKey.id());
        assertEquals("gemini", standardKey.id());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", authKey.baseUrl());
    }

    @Test
    void detectsOpenRouterAndSelectsItsAutoRouter() {
        var profile = registry.detect("sk-or-v1-example").orElseThrow();

        assertEquals("openrouter", profile.id());
        assertEquals("https://openrouter.ai/api/v1", profile.baseUrl());
        assertEquals("openrouter/auto", profile.defaultModel());
    }

    @Test
    void doesNotGuessProviderForUnknownOpaqueKeys() {
        assertTrue(registry.detect("opaque-provider-key-with-no-known-prefix").isEmpty());
    }

    @Test
    void findsExistingMistralConfigurationBySavedBaseUrl() {
        var profile = registry.findByBaseUrl("https://api.mistral.ai/v1").orElseThrow();

        assertEquals("mistral", profile.id());
        assertEquals("Mistral API", registry.displayName(null, profile.baseUrl()));
    }
}
