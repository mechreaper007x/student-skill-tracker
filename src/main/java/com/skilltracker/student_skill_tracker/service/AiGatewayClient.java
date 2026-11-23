package com.skilltracker.student_skill_tracker.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * The application's single AI transport contract: OpenAI-compatible Chat
 * Completions. Model discovery reads the configured API's own /models response;
 * model IDs are never synthesized from a vendor-wide hardcoded catalogue.
 */
@Service
public class AiGatewayClient {

    private static final int MAX_MODEL_PAGES = 30;

    private final RestTemplate restTemplate;

    public AiGatewayClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public String complete(String apiKey, String model, String baseUrl,
            List<Map<String, String>> messages, double temperature, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI API key is required.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI model ID is required.");
        }

        String endpoint = AiGatewayUrlValidator.chatCompletionsUrl(baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey.trim());

        Map<String, Object> body = Map.of(
                "model", model.trim(),
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", messages);

        ResponseEntity<Map> response = restTemplate.exchange(
                endpoint, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("Empty response from AI gateway.");
        }

        Object choicesObject = responseBody.get("choices");
        if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("Invalid OpenAI-compatible chat-completions response.");
        }

        Object content = message.get("content");
        if (content instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        if (content instanceof List<?> parts) {
            StringBuilder text = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> item && item.get("text") != null) {
                    text.append(item.get("text"));
                }
            }
            if (!text.isEmpty()) {
                return text.toString().trim();
            }
        }

        throw new IllegalStateException("AI gateway returned no text content.");
    }

    /**
     * Fetches every model ID exposed by this configured API key. Pagination
     * links are followed only when they remain on the same HTTPS origin, so a
     * provider cannot redirect the bearer token to another host.
     */
    @SuppressWarnings("unchecked")
    public List<String> listModels(String apiKey, String baseUrl) {
        return listModels(apiKey, baseUrl, false);
    }

    public List<String> listModels(String apiKey, String baseUrl, boolean userFilteredCatalog) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI API key is required.");
        }

        String nextPage = userFilteredCatalog
                ? AiGatewayUrlValidator.userModelsUrl(baseUrl)
                : AiGatewayUrlValidator.modelsUrl(baseUrl);
        Set<String> visitedPages = new HashSet<>();
        Set<String> modelIds = new LinkedHashSet<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey.trim());

        for (int page = 0; nextPage != null && page < MAX_MODEL_PAGES; page++) {
            if (!visitedPages.add(nextPage)) {
                break;
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    nextPage, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("The configured API returned an empty model list response.");
            }

            Object modelsObject = body.get("data");
            if (!(modelsObject instanceof List<?>)) {
                modelsObject = body.get("models");
            }
            if (!(modelsObject instanceof List<?> models)) {
                throw new IllegalStateException("The configured API did not return a supported model list.");
            }

            for (Object model : models) {
                String modelId = extractModelId(model);
                if (modelId != null && !modelId.isBlank()) {
                    modelIds.add(modelId);
                }
            }

            nextPage = resolveNextPage(body, nextPage);
        }

        return new ArrayList<>(modelIds);
    }

    private String extractModelId(Object value) {
        if (value instanceof String modelId) {
            return normalizeModelId(modelId);
        }
        if (value instanceof Map<?, ?> model) {
            Object id = model.get("id");
            if (id == null || id.toString().isBlank()) {
                id = model.get("name");
            }
            return id == null ? null : normalizeModelId(id.toString());
        }
        return null;
    }

    private String normalizeModelId(String modelId) {
        String normalized = modelId.trim();
        if (normalized.startsWith("models/")) {
            normalized = normalized.substring("models/".length());
        }
        return normalized;
    }

    private String resolveNextPage(Map<String, Object> body, String currentPage) {
        Object linksObject = body.get("links");
        Object nextObject = linksObject instanceof Map<?, ?> links ? links.get("next") : null;
        if (nextObject == null) {
            nextObject = body.get("next");
        }
        if (!(nextObject instanceof String next) || next.isBlank()) {
            return null;
        }

        URI current = URI.create(currentPage);
        URI resolved = current.resolve(next.trim());
        boolean sameOrigin = "https".equalsIgnoreCase(resolved.getScheme())
                && current.getHost() != null
                && current.getHost().equalsIgnoreCase(resolved.getHost())
                && effectivePort(current) == effectivePort(resolved);
        if (!sameOrigin) {
            throw new IllegalStateException("The provider returned an unsafe model-list pagination link.");
        }
        return resolved.toASCIIString();
    }

    private int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }
}
