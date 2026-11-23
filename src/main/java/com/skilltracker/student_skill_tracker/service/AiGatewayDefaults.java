package com.skilltracker.student_skill_tracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deployment-level defaults. A student's saved gateway configuration takes
 * precedence; these values make a fresh deployment configurable without a
 * source-code change.
 */
@Component
public class AiGatewayDefaults {

    @Value("${AI_BASE_URL:https://api.mistral.ai/v1}")
    private String baseUrl;

    @Value("${AI_PROTOCOL:OPENAI_COMPATIBLE}")
    private String protocol;

    @Value("${AI_MODEL:mistral-small-latest}")
    private String model;

    public String baseUrl() {
        return AiGatewayUrlValidator.normalizeBaseUrl(baseUrl);
    }

    public String protocol() {
        return AiGatewayProtocol.from(protocol).wireName();
    }

    public String model() {
        return model == null || model.isBlank() ? "mistral-small-latest" : model.trim();
    }
}
