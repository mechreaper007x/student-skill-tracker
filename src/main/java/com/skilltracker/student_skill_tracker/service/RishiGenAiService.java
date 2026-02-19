package com.skilltracker.student_skill_tracker.service;

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

@Service
public class RishiGenAiService {

    private static final String MISTRAL_CHAT_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "open-mixtral-8x7b";

    private static final String SYSTEM_PROMPT = """
            You are Rishi, a pragmatic mentor for students.
            Teach clearly with concrete examples and crisp structure.
            For each response:
            1) Explain the concept in simple terms.
            2) Show a practical example.
            3) Give a short exercise for practice.
            4) Give next-step resources.
            Keep tone direct and helpful.
            """;

    private final RestTemplate restTemplate;

    public RishiGenAiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateLearningResponse(String apiKey, String model, String userMessage) {
        return generateLearningResponse(apiKey, model, userMessage, List.of());
    }

    public String generateLearningResponse(String apiKey, String model, String userMessage,
            List<Map<String, String>> memoryContext) {
        validate(apiKey, userMessage);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        if (memoryContext != null) {
            for (Map<String, String> m : memoryContext) {
                if (m == null)
                    continue;
                String role = m.get("role");
                String content = m.get("content");
                if (isBlank(role) || isBlank(content))
                    continue;

                String normalizedRole = role.trim().toLowerCase();
                if (!"user".equals(normalizedRole) && !"assistant".equals(normalizedRole)) {
                    continue;
                }
                messages.add(Map.of("role", normalizedRole, "content", content.trim()));
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage.trim()));
        return callMistral(apiKey, model, messages, 0.7, 900);
    }

    public String generateStudyPlan(String apiKey, String model, String topic, String goals, Integer durationDays,
            String skillSnapshot, List<Map<String, String>> memoryContext) {
        if (isBlank(topic)) {
            throw new IllegalArgumentException("Topic is required for study plan generation.");
        }

        int safeDuration = sanitizeDurationDays(durationDays);
        String safeGoals = isBlank(goals) ? "No explicit goals provided." : goals.trim();
        String safeSkillSnapshot = isBlank(skillSnapshot) ? "No tracked skill data available." : skillSnapshot.trim();

        String prompt = """
                Build a personalized study plan.
                Constraints:
                - Duration: %d days
                - Topic: %s
                - Student goals: %s
                - Skill snapshot: %s

                Output format:
                1) Plan overview (3-5 lines)
                2) Daily schedule with Day 1 ... Day %d
                3) Two checkpoints (mid and final)
                4) One mini-project idea tied to the topic
                5) Common pitfalls and mitigation
                Keep it practical and specific.
                """.formatted(safeDuration, topic.trim(), safeGoals, safeSkillSnapshot, safeDuration);

        return generateLearningResponse(apiKey, model, prompt, memoryContext);
    }

    @SuppressWarnings("unchecked")
    private String callMistral(String apiKey, String model, List<Map<String, String>> messages, double temperature,
            int maxTokens) {
        String selectedModel = isBlank(model) ? DEFAULT_MODEL : model.trim();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(apiKey.trim());

        Map<String, Object> body = Map.of(
                "model", selectedModel,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", messages);

        ResponseEntity<Map> response = restTemplate.exchange(
                MISTRAL_CHAT_URL,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("Empty response from GenAI provider.");
        }

        Object choicesObj = responseBody.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("Invalid response from GenAI provider.");
        }

        Object firstChoiceObj = choices.get(0);
        if (!(firstChoiceObj instanceof Map<?, ?> firstChoice)) {
            throw new IllegalStateException("Invalid choice format from GenAI provider.");
        }

        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new IllegalStateException("Invalid message format from GenAI provider.");
        }

        Object contentObj = messageMap.get("content");
        if (contentObj == null) {
            throw new IllegalStateException("No content in GenAI response.");
        }

        return String.valueOf(contentObj).trim();
    }

    private int sanitizeDurationDays(Integer durationDays) {
        if (durationDays == null || durationDays < 1) {
            return 7;
        }
        return Math.min(durationDays, 60);
    }

    private void validate(String apiKey, String userMessage) {
        if (isBlank(apiKey)) {
            throw new IllegalArgumentException("GenAI API key is required.");
        }
        if (isBlank(userMessage)) {
            throw new IllegalArgumentException("Message is required.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
