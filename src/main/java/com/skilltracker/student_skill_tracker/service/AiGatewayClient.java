package com.skilltracker.student_skill_tracker.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

/**
 * Provider-neutral chat client for the protocols exposed by common AI gateways.
 *
 * OpenAI-compatible covers gateways such as OpenRouter and LiteLLM and
 * compatible model endpoints (including many Qwen and Mistral endpoints).
 * Anthropic and Gemini direct APIs use their native request/response formats.
 */
@Service
public class AiGatewayClient {

    private final RestTemplate restTemplate;

    public AiGatewayClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public String complete(String apiKey, String model, String baseUrl, String protocol,
            List<Map<String, String>> messages, double temperature, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("AI API key is required.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI model ID is required.");
        }

        AiGatewayProtocol selectedProtocol = AiGatewayProtocol.from(protocol);
        String safeBaseUrl = AiGatewayUrlValidator.normalizeBaseUrl(baseUrl);

        return switch (selectedProtocol) {
            case OPENAI_COMPATIBLE -> openAiCompatible(apiKey, model, safeBaseUrl, messages, temperature, maxTokens);
            case ANTHROPIC -> anthropic(apiKey, model, safeBaseUrl, messages, temperature, maxTokens);
            case GEMINI -> gemini(apiKey, model, safeBaseUrl, messages, temperature, maxTokens);
        };
    }

    @SuppressWarnings("unchecked")
    private String openAiCompatible(String apiKey, String model, String baseUrl,
            List<Map<String, String>> messages, double temperature, int maxTokens) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(apiKey.trim());

        Map<String, Object> body = Map.of(
                "model", model.trim(),
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", messages);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/chat/completions", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        Map<String, Object> responseBody = requireBody(response.getBody());
        Object choicesObj = responseBody.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("Invalid chat-completions response from AI gateway.");
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

    @SuppressWarnings("unchecked")
    private String anthropic(String apiKey, String model, String baseUrl,
            List<Map<String, String>> messages, double temperature, int maxTokens) {
        HttpHeaders headers = jsonHeaders();
        headers.set("x-api-key", apiKey.trim());
        headers.set("anthropic-version", "2023-06-01");

        StringBuilder system = new StringBuilder();
        List<Map<String, String>> conversation = new ArrayList<>();
        for (Map<String, String> message : messages) {
            if (message == null || message.get("content") == null) {
                continue;
            }
            String role = message.get("role");
            if ("system".equalsIgnoreCase(role)) {
                if (!system.isEmpty()) system.append("\n\n");
                system.append(message.get("content"));
            } else if ("user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role)) {
                conversation.add(Map.of("role", role.toLowerCase(), "content", message.get("content")));
            }
        }
        if (conversation.isEmpty()) {
            throw new IllegalArgumentException("At least one user message is required.");
        }

        Map<String, Object> body = Map.of(
                "model", model.trim(),
                "max_tokens", maxTokens,
                "temperature", temperature,
                "system", system.toString(),
                "messages", conversation);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/messages", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        Map<String, Object> responseBody = requireBody(response.getBody());
        Object contentObj = responseBody.get("content");
        if (!(contentObj instanceof List<?> content)) {
            throw new IllegalStateException("Invalid Anthropic Messages response.");
        }
        StringBuilder text = new StringBuilder();
        for (Object item : content) {
            if (item instanceof Map<?, ?> block && "text".equals(block.get("type")) && block.get("text") != null) {
                text.append(block.get("text"));
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Anthropic returned no text content.");
        }
        return text.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String gemini(String apiKey, String model, String baseUrl,
            List<Map<String, String>> messages, double temperature, int maxTokens) {
        StringBuilder system = new StringBuilder();
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Map<String, String> message : messages) {
            if (message == null || message.get("content") == null) {
                continue;
            }
            String role = message.get("role");
            if ("system".equalsIgnoreCase(role)) {
                if (!system.isEmpty()) system.append("\n\n");
                system.append(message.get("content"));
            } else if ("user".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role)) {
                contents.add(Map.of(
                        "role", "assistant".equalsIgnoreCase(role) ? "model" : "user",
                        "parts", List.of(Map.of("text", message.get("content")))));
            }
        }
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("At least one user message is required.");
        }

        Map<String, Object> generationConfig = Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxTokens);
        Map<String, Object> body = system.isEmpty()
                ? Map.of("contents", contents, "generationConfig", generationConfig)
                : Map.of("contents", contents,
                        "systemInstruction", Map.of("parts", List.of(Map.of("text", system.toString()))),
                        "generationConfig", generationConfig);

        String encodedModel = UriUtils.encodePathSegment(model.trim(), StandardCharsets.UTF_8);
        String url = baseUrl + "/models/" + encodedModel + ":generateContent?key="
                + UriUtils.encodeQueryParam(apiKey.trim(), StandardCharsets.UTF_8);
        HttpHeaders headers = jsonHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        Map<String, Object> responseBody = requireBody(response.getBody());
        Object candidatesObj = responseBody.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()
                || !(candidates.get(0) instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) {
            throw new IllegalStateException("Invalid Gemini generateContent response.");
        }
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> item && item.get("text") != null) {
                text.append(item.get("text"));
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini returned no text content.");
        }
        return text.toString().trim();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private Map<String, Object> requireBody(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalStateException("Empty response from AI provider.");
        }
        return body;
    }
}
