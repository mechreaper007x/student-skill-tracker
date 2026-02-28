package com.skilltracker.student_skill_tracker.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class RishiGenAiService {

    private static final String MISTRAL_CHAT_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "open-mixtral-8x7b";
    private static final int CHAT_MAX_TOKENS = 1400;
    private static final int CODE_REVIEW_MAX_TOKENS = 1800;
    private static final int STUDY_PLAN_MAX_TOKENS = 2200;
    private static final double CHAT_TEMPERATURE = 0.7;
    private static final double CODE_REVIEW_TEMPERATURE = 0.3;
    private static final double STUDY_PLAN_TEMPERATURE = 0.4;
    private static final int MAX_USER_MESSAGE_CHARS = 7000;
    private static final int MAX_MEMORY_MESSAGE_CHARS = 1800;
    private static final int MAX_CONTEXT_PACKET_CHARS = 14000;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final RestTemplate restTemplate;

    public RishiGenAiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateLearningResponse(String apiKey, String model, String userMessage,
            List<Map<String, String>> memoryContext, String studentContextStr) {
        validate(apiKey, userMessage);
        GenerationPolicy policy = detectPolicy(userMessage, false);
        return generateWithPolicy(apiKey, model, userMessage, memoryContext, studentContextStr, policy);
    }

    public String generateStudyPlan(String apiKey, String model, String topic, String goals, Integer durationDays,
            String skillSnapshot, List<Map<String, String>> memoryContext) {
        if (isBlank(topic)) {
            throw new IllegalArgumentException("Topic is required for study plan generation.");
        }

        int safeDuration = sanitizeDurationDays(durationDays);
        String safeGoals = isBlank(goals) ? "No explicit goals provided." : goals.trim();
        String safeSnapshot = isBlank(skillSnapshot) ? "No tracked skill data available." : skillSnapshot.trim();

        String prompt = """
                Activate Mode 1: STUDY PLAN ARCHITECT.
                Topic: %s
                DurationDays: %d
                StudentGoals: %s
                LiveContextPacket:
                %s

                Non-negotiable study plan constraints:
                - Provide day-by-day schedule.
                - Include concrete LeetCode slugs for each day.
                - Include Day 3 and Day 7 checkpoints.
                - Match pacing to cognitive state and thinking style.
                - Include exactly one next action at the end.
                """.formatted(topic.trim(), safeDuration, safeGoals, safeSnapshot);

        GenerationPolicy policy = detectPolicy(prompt, true);
        return generateWithPolicy(apiKey, model, prompt, memoryContext, safeSnapshot, policy);
    }

    private String generateWithPolicy(String apiKey, String model, String userMessage,
            List<Map<String, String>> memoryContext, String studentContextStr, GenerationPolicy policy) {
        String safeUserMessage = sanitizeAndTruncate(userMessage, MAX_USER_MESSAGE_CHARS);
        String safeStudentContext = isBlank(studentContextStr) ? "{\"message\":\"No live context packet available.\"}"
                : sanitizeAndTruncate(studentContextStr, MAX_CONTEXT_PACKET_CHARS);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(safeStudentContext)));

        if (memoryContext != null) {
            for (Map<String, String> m : memoryContext) {
                if (m == null) {
                    continue;
                }
                String role = m.get("role");
                String content = m.get("content");
                if (isBlank(role) || isBlank(content)) {
                    continue;
                }
                String normalizedRole = role.trim().toLowerCase();
                if (!"user".equals(normalizedRole) && !"assistant".equals(normalizedRole)) {
                    continue;
                }
                messages.add(Map.of(
                        "role", normalizedRole,
                        "content", sanitizeAndTruncate(content.trim(), MAX_MEMORY_MESSAGE_CHARS)));
            }
        }

        messages.add(Map.of("role", "user", "content", safeUserMessage));
        return callMistral(apiKey, model, messages, policy.temperature(), policy.maxTokens());
    }

    private String buildSystemPrompt(String studentContext) {
        return """
                RISHI AGENTIC MENTOR SYSTEM v3
                You are Rishi, a 10x Senior Developer + cognitive coach embedded in StudentSkillTracker.
                Your mission is measurable cognition improvement, not generic chat.

                LIVE CONTEXT PACKET:
                %s

                STEP 0 — SILENT INTERNAL ANALYSIS (DO NOT REVEAL)
                - Compute WEAK_SCORE = min(problemSolvingScore, algorithmsScore, dataStructuresScore)
                - Compute COGNITIVE_STATE from eqScore + emotion trends:
                  FRAGILE / PRIMED / NEUTRAL
                - Compute BLOOM_CEILING from highestBloomLevel and recent duel profile
                - Compute SM-2 decay status from review metadata in context (Critical/Warning/Stable/Overdue)
                - Decide response intent: corrective / technical / motivational

                STEP 1 — MODE AUTO-DETECTION
                Detect exactly one dominant mode from user intent:
                1) STUDY_PLAN_ARCHITECT trigger words: study plan, roadmap, day-by-day, schedule, topic guide
                2) LIVE_CODE_REVIEW trigger words: code block, pasted code, review this code
                3) MOCK_INTERVIEW trigger words: mock interview, interview me, FAANG practice
                4) DUEL_COACH trigger words: duel result, won/lost duel, arena score, opponent
                5) WHITEBOARD_VISUALIZER trigger words: explain, visualize, show me, how does this work
                6) EQ_COACH trigger words: frustrated, failed, stuck, tilt, burned out

                STEP 2 — ADAPTIVE PERSONALIZATION
                - If thinkingStyle=system1_dominant -> force deliberate step-by-step reasoning.
                - If thinkingStyle=system2_deliberate -> allow deeper technical depth.
                - If COGNITIVE_STATE=FRAGILE -> shorter, one-action pacing.
                - If COGNITIVE_STATE=PRIMED -> increase challenge and rigor.
                - Match problem difficulty to weak scores and Bloom ceiling.

                STEP 3 — NON-NEGOTIABLE RULES
                - Always ground advice in numeric context.
                - Never give empty praise.
                - Never dump complete solutions before student attempt in interview/review contexts.
                - Ask exactly one probing question per response.
                - End every response with exactly one “🎯 Next Action”.
                - For DS/algorithm explanation mode, prefer Mermaid diagrams when structurally useful.

                STEP 4 — FORMAT STANDARDS
                - STUDY_PLAN_ARCHITECT: concise day plan, checkpoints, pitfalls, one next action.
                - LIVE_CODE_REVIEW: Critical Issues, Optimization, Strengths (only if earned), Concept Gap.
                - MOCK_INTERVIEW: interviewer style, no direct solution, evaluate communication and complexity.
                - DUEL_COACH: diagnose round/Bloom gap and assign targeted drill.
                - WHITEBOARD_VISUALIZER: concrete example + structured steps.
                - EQ_COACH: one-line acknowledgement then tactical correction.
                """.formatted(studentContext);
    }

    private GenerationPolicy detectPolicy(String userMessage, boolean forceStudyPlan) {
        if (forceStudyPlan) {
            return new GenerationPolicy(STUDY_PLAN_TEMPERATURE, STUDY_PLAN_MAX_TOKENS);
        }
        String normalized = userMessage == null ? "" : userMessage.toLowerCase();
        boolean isStudyPlan = normalized.contains("study plan")
                || normalized.contains("roadmap")
                || normalized.contains("day 1")
                || normalized.contains("schedule");
        boolean isCodeReview = normalized.contains("code review")
                || normalized.contains("review this code")
                || normalized.contains("time complexity")
                || normalized.contains("big-o")
                || normalized.contains("```");

        if (isStudyPlan) {
            return new GenerationPolicy(STUDY_PLAN_TEMPERATURE, STUDY_PLAN_MAX_TOKENS);
        }
        if (isCodeReview) {
            return new GenerationPolicy(CODE_REVIEW_TEMPERATURE, CODE_REVIEW_MAX_TOKENS);
        }
        return new GenerationPolicy(CHAT_TEMPERATURE, CHAT_MAX_TOKENS);
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

        int attempt = 0;
        while (true) {
            try {
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
                String content = extractContent(messageMap.get("content"));
                if (isBlank(content)) {
                    throw new IllegalStateException("No content in GenAI response.");
                }
                return content.trim();
            } catch (HttpStatusCodeException ex) {
                attempt++;
                if (attempt < MAX_RETRY_ATTEMPTS && isRetryableStatus(ex.getStatusCode().value())) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw new IllegalStateException("Mistral API request failed: HTTP " + ex.getStatusCode().value(), ex);
            } catch (ResourceAccessException ex) {
                attempt++;
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw new IllegalStateException("Mistral API request failed due to network timeout.", ex);
            }
        }
    }

    private String extractContent(Object contentObj) {
        if (contentObj == null) {
            return "";
        }
        if (contentObj instanceof String content) {
            return content;
        }
        if (contentObj instanceof List<?> parts) {
            StringBuilder builder = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> partMap) {
                    Object textObj = partMap.get("text");
                    if (textObj != null) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(String.valueOf(textObj));
                    }
                }
            }
            return builder.toString();
        }
        return String.valueOf(contentObj);
    }

    private String sanitizeAndTruncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace('\u0000', ' ').trim();
        if (sanitized.length() <= maxChars) {
            return sanitized;
        }
        return sanitized.substring(0, maxChars) + " [truncated]";
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || (statusCode >= 500 && statusCode <= 599);
    }

    private void sleepBackoff(int attempt) {
        long millis = switch (attempt) {
            case 1 -> 350L;
            case 2 -> 900L;
            default -> 1800L;
        };
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
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

    private record GenerationPolicy(double temperature, int maxTokens) {
    }
}
