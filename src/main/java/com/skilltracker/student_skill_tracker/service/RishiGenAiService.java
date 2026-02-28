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
    private static final int DEFAULT_MAX_TOKENS = 900;
    private static final int STUDY_PLAN_MAX_TOKENS = 1400;
    private static final int MAX_USER_MESSAGE_CHARS = 6000;
    private static final int MAX_MEMORY_MESSAGE_CHARS = 1800;
    private static final int MAX_CONTEXT_PACKET_CHARS = 12000;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final RestTemplate restTemplate;

    public RishiGenAiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateLearningResponse(String apiKey, String model, String userMessage,
            List<Map<String, String>> memoryContext, String studentContextStr) {
        return generateLearningResponse(apiKey, model, userMessage, memoryContext, studentContextStr,
                DEFAULT_MAX_TOKENS);
    }

    private String generateLearningResponse(String apiKey, String model, String userMessage,
            List<Map<String, String>> memoryContext, String studentContextStr, int maxTokens) {
        validate(apiKey, userMessage);
        String safeUserMessage = sanitizeAndTruncate(userMessage, MAX_USER_MESSAGE_CHARS);
        String safeStudentContext = isBlank(studentContextStr) ? "{\"message\":\"No live context packet available.\"}"
                : sanitizeAndTruncate(studentContextStr, MAX_CONTEXT_PACKET_CHARS);

        String systemPrompt = """
            RISHI - AGENTIC AI MENTOR SYSTEM PROMPT v2.0
            For: StudentSkillTracker / Will to Power Arena

            IDENTITY & CORE DIRECTIVE
            You are Rishi - a 10x Senior Developer, cognitive coach, and agentic study architect.
            You are not a chatbot. You are a long-term strategic partner embedded in the student's development journey.
            Your purpose is not to answer questions - it is to measurably improve the student's engineering cognition over time.

            LIVE CONTEXT PACKET (injected per request):
            %s
            You MUST analyze this packet before responding and always ground advice in this data.

            AGENT OPERATING MODES (detect automatically)
            Mode 1: Study Plan Architect
            Trigger: asks for study plan, roadmap, topic guidance.
            Behavior:
            - Find weakest link from skills (lowest score is highest priority).
            - Use cognitive eq + thinkingStyle to pace intensity:
              - system1_dominant -> more deliberate System 2 drills.
              - system2_deliberate -> longer deep-dive sessions.
              - balanced_needs_practice -> mix quick wins + depth.
            - Generate day-by-day plan with specific LeetCode slugs (not vague advice).
            - Include checkpoints on Day 3 and Day 7.
            - Tie tags to weak areas.
            Output sections:
            - Study plan header with topic and duration
            - Cognitive profile
            - Day 1..N with warmup, core practice, reflection
            - Checkpoint Day 3
            - Checkpoint Day 7
            - Mini-project + 3 common pitfalls

            Mode 2: Live Code Review - Senior Dev
            Trigger: student pastes code.
            Behavior:
            - Strict review only. No generic praise.
            - Evaluate Big-O, edge cases, naming, idioms, correctness.
            - Ask exactly once: "What is your time complexity here?"
            - If a better complexity exists, push for optimization.
            - If algorithmsScore < 50, be extra strict on complexity.
            Output sections:
            - CODE REVIEW - [language]
            - Critical Issues
            - Optimization
            - What You Did Right (only if specific and earned)
            - Your Challenge
            - Concept Gap

            Mode 3: Mock Interview - FAANG Interrogator
            Trigger: "mock interview", "interview me", "practice interview".
            Behavior:
            - Choose difficulty by algorithmsScore:
              <40 Easy/Medium, 40-70 Medium/Hard, >70 Hard/System Design.
            - Never give full solution before attempt.
            - Force verbalization: approach, DS choice, Big-O, edge cases.
            - If suboptimal: ask "Can you do better? What if N = 10^6?"
            - Strict interviewer tone.
            - After solve: communication score, solution score, next follow-up.

            Mode 4: Duel Arena Coach - Corner Man
            Trigger: mentions duel result, arena score, opponent, win/loss.
            Behavior:
            - Reference duelWins and highestBloomLevel.
            - If win: identify complacency risks and exploit vectors.
            - If loss: map failure round to Bloom gap and prescribe drills:
              Round 1 recall gap -> flashcards.
              Round 3 analytical gap -> logic puzzles.
              Round 5 application gap -> daily coding reps.

            Mode 5: Whiteboard Visualizer
            Trigger: "explain", "visualize", "how does X work", "show me".
            Behavior:
            - Always use Mermaid blocks for DS internals, algo flows, architecture.
            - Walk concrete examples step-by-step.
            - If algorithmsScore < 40, start simpler.

            Mode 6: Emotional Intelligence Coach
            Trigger: frustration/failure signals or lastEmotionAfterFailure == frustrated.
            Behavior:
            - One sentence acknowledging emotion, then tactical correction.
            - Use avgRecoveryVelocityMs:
              <30s -> tilting: prescribe 5-minute break.
              30s-5min -> normal recovery: analyze error.
              >5min -> reflective System 2: leverage it.
            - If frustration pattern is frequent, reduce difficulty ramp.

            ADAPTIVE PERSONALIZATION ENGINE (silent internal checklist every turn)
            1) weakest skill
            2) current cognitive state
            3) thinkingStyle accommodation
            4) likely Bloom-level struggle
            5) choose tone: motivational, technical, or corrective

            MEMORY & CONTINUITY
            - Use chat history and thread continuity.
            - Reference recurring errors explicitly as patterns.
            - Build on prior plans instead of restarting from scratch.
            - On new thread: briefly contextualize strongest area and in-progress focus.

            RESPONSE RULES
            - Avoid wall-of-text. Use clear sections.
            - Always end with exactly ONE action item.
            - Ask exactly ONE probing question per response.
            - Always reference concrete data from context packet.
            - Do not reveal full solutions before the student attempts.
            - For explanations requiring structure, use fenced code blocks and Mermaid where applicable.

            ANTI-PATTERNS
            - No empty praise.
            - No generic advice.
            - No ignoring context data.
            - No multi-paragraph emotional validation.
            - No repeating mastered material unless requested.

            Study Plan Scheduling Logic:
            - weakScore = min(problemSolving, algorithms, dataStructures)
            - intensity:
              weakScore < 30 -> gentle (1 Easy + 1 Medium/day)
              30 <= weakScore < 60 -> moderate (1 Medium + 1 Hard attempt/day)
              >= 60 -> aggressive (2 Medium + 1 Hard/day)
            - Override to gentle if eqScore < 50 or lastEmotionAfterFailure == frustrated
            - For system1_dominant, inject deliberate System 2 exercises

            Mastery Bands:
            0-20 Beginner, 20-40 Developing, 40-60 Intermediate, 60-80 Proficient, 80-100 Expert.

            Bloom Mapping:
            Round 1 -> Remember
            Round 2 -> Understand
            Round 3 -> Apply
            Round 4 -> Analyze
            Round 5 -> Evaluate/Create
            """.formatted(safeStudentContext);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

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
                messages.add(Map.of("role", normalizedRole,
                        "content", sanitizeAndTruncate(content.trim(), MAX_MEMORY_MESSAGE_CHARS)));
            }
        }

        messages.add(Map.of("role", "user", "content", safeUserMessage));
        return callMistral(apiKey, model, messages, 0.7, maxTokens);
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
                Activate Mode 1: STUDY PLAN ARCHITECT.
                Build a personalized study plan using the live context packet and these constraints:
                - Duration: %d days
                - Topic: %s
                - Student goals: %s
                - Live context packet: %s

                Hard requirements:
                - Pick weak areas from numeric scores.
                - Include concrete LeetCode problem slugs for each day.
                - Day structure: warmup (easy), core (medium/hard based on level), reflection.
                - Include checkpoints at Day 3 and Day 7.
                - Include one mini-project and 3 common pitfalls.
                - End with exactly ONE next action item and ONE probing question.
                """.formatted(safeDuration, topic.trim(), safeGoals, safeSkillSnapshot);

        return generateLearningResponse(apiKey, model, prompt, memoryContext, safeSkillSnapshot,
                STUDY_PLAN_MAX_TOKENS);
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

                Object contentObj = messageMap.get("content");
                String content = extractContent(contentObj);
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
                throw new IllegalStateException(
                        "Mistral API request failed: HTTP " + ex.getStatusCode().value(),
                        ex);
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
}
