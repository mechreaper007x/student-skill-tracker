package com.skilltracker.student_skill_tracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deployment defaults. A student's saved gateway configuration takes
 * precedence.
 */
@Component
public class AiGatewayDefaults {

    @Value("${AI_BASE_URL:https://api.mistral.ai/v1}")
    private String baseUrl;

    @Value("${AI_MODEL:mistral-small-latest}")
    private String model;

    public String baseUrl() {
        return AiGatewayUrlValidator.normalizeBaseUrl(baseUrl);
    }

    public String model() {
        return model == null || model.isBlank() ? "mistral-small-latest" : model.trim();
    }
}
