package com.skilltracker.student_skill_tracker.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * The application's single AI transport contract: OpenAI-compatible Chat
 * Completions. Provider routing belongs in the configured gateway, not here.
 */
@Service
public class AiGatewayClient {

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
}
