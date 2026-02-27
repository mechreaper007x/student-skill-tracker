package com.skilltracker.student_skill_tracker.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@Service
public class CognitiveMetricService {

    private static final Logger logger = LoggerFactory.getLogger(CognitiveMetricService.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String GEMINI_MODEL = "gemini-2.0-flash";
    private static final long SYSTEM1_LIMIT_MS = 8_000;
    private static final int MAX_EMOTION_LOG_ENTRIES = 200;

    private final StudentRepository studentRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final ConcurrentMap<String, CognitiveSprintSession> activeSprints = new ConcurrentHashMap<>();

    private final List<SprintQuestionPair> sprintFallbackBank = List.of(
            new SprintQuestionPair(
                    new SprintQuestion(
                            "What is the time complexity of binary search on a sorted array?",
                            List.of("O(1)", "O(log n)", "O(n)", "O(n log n)"),
                            1),
                    new SprintQuestion(
                            "For n=1024 sorted values, binary search needs at most how many comparisons?",
                            List.of("5", "8", "10", "16"),
                            2)),
            new SprintQuestionPair(
                    new SprintQuestion(
                            "Which data structure follows FIFO order?",
                            List.of("Stack", "Queue", "Heap", "Trie"),
                            1),
                    new SprintQuestion(
                            "Which operation pair best maps to a queue?",
                            List.of("push/pop", "enqueue/dequeue", "insert/deleteMin", "append/removeLast"),
                            1)),
            new SprintQuestionPair(
                    new SprintQuestion(
                            "Which traversal uses a queue?",
                            List.of("DFS", "BFS", "Inorder", "Postorder"),
                            1),
                    new SprintQuestion(
                            "The shortest path in an unweighted graph is classically found using:",
                            List.of("DFS", "BFS", "Dijkstra only", "Floyd-Warshall only"),
                            1)));

    public CognitiveMetricService(StudentRepository studentRepository, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.studentRepository = studentRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public void recordCompilation(Student student, boolean success) {
        student.setTotalCompilations(student.getTotalCompilations() + 1);
        if (success) {
            student.setSuccessfulCompilations(student.getSuccessfulCompilations() + 1);
        }
        studentRepository.save(student);
        logger.debug("Recorded compilation for {}: success={}", student.getEmail(), success);
    }

    @Transactional
    public void recordSubmission(Student student, String problemSlug, boolean accepted) {
        student.setTotalSubmissions(student.getTotalSubmissions() + 1);
        if (accepted) {
            student.setAcceptedSubmissions(student.getAcceptedSubmissions() + 1);
        } else {
            student.setLastFailureTimestamp(LocalDateTime.now());
        }

        studentRepository.save(student);
        logger.debug("Recorded submission for {}: problem={}, accepted={}", student.getEmail(), problemSlug, accepted);
    }

    @Transactional
    public void recordQuestionSelection(Student student, String slug) {
        student.setLastSelectedQuestionSlug(slug);
        student.setQuestionSelectionTimestamp(LocalDateTime.now());
        studentRepository.save(student);
    }

    @Transactional
    public void trackPlanningTime(Student student, String slug) {
        if (slug == null || !slug.equals(student.getLastSelectedQuestionSlug()) || student.getQuestionSelectionTimestamp() == null) {
            return;
        }

        long planningTime = Duration.between(student.getQuestionSelectionTimestamp(), LocalDateTime.now()).toMillis();
        long currentAvg = student.getAvgPlanningTimeMs();
        int totalEvents = student.getTotalCompilations() + student.getTotalSubmissions();
        long newAvg = (currentAvg * totalEvents + planningTime) / (totalEvents + 1);

        student.setAvgPlanningTimeMs(newAvg);
        student.setQuestionSelectionTimestamp(null);
        studentRepository.save(student);
        logger.info("Tracked planning time for {}: {}ms", student.getEmail(), planningTime);
    }

    @Transactional
    public void trackRecoveryVelocity(Student student) {
        if (student.getLastFailureTimestamp() == null) {
            return;
        }

        long recoveryTime = Duration.between(student.getLastFailureTimestamp(), LocalDateTime.now()).toMillis();
        long currentAvg = student.getAvgRecoveryVelocityMs();
        int totalSubmissions = student.getTotalSubmissions();
        long newAvg = (currentAvg * totalSubmissions + recoveryTime) / (totalSubmissions + 1);

        student.setAvgRecoveryVelocityMs(newAvg);
        student.setLastFailureTimestamp(null);
        studentRepository.save(student);
        logger.info("Tracked recovery velocity for {}: {}ms", student.getEmail(), recoveryTime);
    }

    public Map<String, Object> startCognitiveSprint(Student student) {
        SprintQuestionPair pair = generateSprintQuestionsWithFallback(student);
        String sprintId = UUID.randomUUID().toString();

        CognitiveSprintSession session = new CognitiveSprintSession(
                sprintId,
                student.getId(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                pair,
                SprintStage.ROUND_A_PENDING);
        activeSprints.put(sprintId, session);

        return Map.of(
                "sprintId", sprintId,
                "system1TimeLimitSeconds", 8,
                "roundA", questionPayload(pair.roundA()),
                "roundB", questionPayload(pair.roundB()));
    }

    public Map<String, Object> submitSprintRoundA(Student student, String sprintId, Integer selectedIndex) {
        CognitiveSprintSession session = requireSprintSession(student, sprintId);
        if (session.stage() != SprintStage.ROUND_A_PENDING) {
            throw new IllegalArgumentException("Round A has already been submitted.");
        }

        int answer = selectedIndex == null ? -1 : selectedIndex;
        LocalDateTime answeredAt = LocalDateTime.now();
        long elapsedMs = Duration.between(session.roundAStart(), answeredAt).toMillis();
        boolean withinLimit = elapsedMs <= SYSTEM1_LIMIT_MS;
        boolean correct = withinLimit && answer == session.questionPair().roundA().correctIndex();

        CognitiveSprintSession updated = session.withRoundA(answer, answeredAt, correct);
        activeSprints.put(sprintId, updated);

        return Map.of(
                "sprintId", sprintId,
                "round", "A",
                "withinTimeLimit", withinLimit,
                "timeTakenMs", elapsedMs,
                "correct", correct,
                "nextRound", "B");
    }

    @Transactional
    public Map<String, Object> submitSprintRoundB(Student student, String sprintId, Integer selectedIndex) {
        CognitiveSprintSession session = requireSprintSession(student, sprintId);
        if (session.stage() != SprintStage.ROUND_B_PENDING) {
            throw new IllegalArgumentException("Round B is not ready yet. Submit Round A first.");
        }

        int answer = selectedIndex == null ? -1 : selectedIndex;
        LocalDateTime answeredAt = LocalDateTime.now();
        long elapsedMs = Duration.between(session.roundBStart(), answeredAt).toMillis();
        boolean roundBCorrect = answer == session.questionPair().roundB().correctIndex();

        boolean roundACorrect = Boolean.TRUE.equals(session.roundACorrect());
        String thinkingStyle = inferThinkingStyle(roundACorrect, roundBCorrect);
        student.setThinkingStyle(thinkingStyle);
        studentRepository.save(student);
        activeSprints.remove(sprintId);

        return Map.of(
                "sprintId", sprintId,
                "roundA", Map.of("correct", roundACorrect),
                "roundB", Map.of("correct", roundBCorrect, "timeTakenMs", elapsedMs),
                "thinkingStyle", thinkingStyle);
    }

    @Transactional
    public void recordEmotionAfterFailure(Student student, String emotion) {
        String normalizedEmotion = normalizeEmotion(emotion);
        student.setLastEmotionAfterFailure(normalizedEmotion);

        ArrayNode emotionLog = parseEmotionLog(student.getEmotionLogJson());
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("emotion", normalizedEmotion);
        entry.put("timestamp", LocalDateTime.now().toString());
        emotionLog.add(entry);

        while (emotionLog.size() > MAX_EMOTION_LOG_ENTRIES) {
            emotionLog.remove(0);
        }

        student.setEmotionLogJson(emotionLog.toString());
        studentRepository.save(student);
    }

    private SprintQuestionPair generateSprintQuestionsWithFallback(Student student) {
        try {
            JsonNode root = callGeminiForJson(buildSprintPrompt());
            SprintQuestion roundA = parseSprintQuestion(root.path("roundA"));
            SprintQuestion roundB = parseSprintQuestion(root.path("roundB"));
            return new SprintQuestionPair(roundA, roundB);
        } catch (Exception ex) {
            logger.warn("Falling back to static sprint question set: {}", ex.getMessage());
            int index = Math.floorMod((student.getId() == null ? 0 : student.getId().intValue()) + LocalDateTime.now().getSecond(),
                    sprintFallbackBank.size());
            return sprintFallbackBank.get(index);
        }
    }

    private JsonNode callGeminiForJson(String prompt) throws Exception {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("gemini.api.key is missing.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = GEMINI_BASE_URL + GEMINI_MODEL + ":generateContent?key=" + geminiApiKey;
        String responseRaw = restTemplate.postForObject(url, request, String.class);
        JsonNode root = objectMapper.readTree(responseRaw);
        JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new IllegalArgumentException("Gemini response text is missing.");
        }

        String jsonPayload = cleanMarkdownWrapper(textNode.asText().trim());
        return objectMapper.readTree(jsonPayload);
    }

    private SprintQuestion parseSprintQuestion(JsonNode node) {
        String prompt = node.path("prompt").asText("").trim();
        JsonNode optionsNode = node.path("options");
        int correctIndex = node.path("correctIndex").asInt(-1);

        if (prompt.isBlank() || !optionsNode.isArray() || optionsNode.size() != 4 || correctIndex < 0 || correctIndex > 3) {
            throw new IllegalArgumentException("Invalid sprint question format.");
        }

        List<String> options = new java.util.ArrayList<>(4);
        for (JsonNode optionNode : optionsNode) {
            String option = optionNode.asText("").trim();
            if (option.isBlank()) {
                throw new IllegalArgumentException("Sprint option cannot be blank.");
            }
            options.add(option);
        }

        return new SprintQuestion(prompt, List.copyOf(options), correctIndex);
    }

    private String buildSprintPrompt() {
        return """
                You are generating a cognitive sprint test for coding students.
                Return ONLY valid raw JSON (no markdown) with EXACTLY this schema:
                {
                  "roundA": {
                    "prompt": "short MCQ prompt",
                    "options": ["opt1", "opt2", "opt3", "opt4"],
                    "correctIndex": 0
                  },
                  "roundB": {
                    "prompt": "short MCQ prompt, same topic and similar difficulty as roundA",
                    "options": ["opt1", "opt2", "opt3", "opt4"],
                    "correctIndex": 0
                  }
                }

                Rules:
                - Topic must be beginner/intermediate CS or DSA.
                - Round A should be quick-intuition friendly.
                - Round B should require a bit more deliberate thought, but similar difficulty.
                - Both rounds must each have exactly 4 options and one correct answer.
                - correctIndex must be an integer from 0 to 3.
                """;
    }

    private String cleanMarkdownWrapper(String text) {
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }

    private CognitiveSprintSession requireSprintSession(Student student, String sprintId) {
        if (sprintId == null || sprintId.isBlank()) {
            throw new IllegalArgumentException("sprintId is required.");
        }

        CognitiveSprintSession session = activeSprints.get(sprintId);
        if (session == null) {
            throw new IllegalArgumentException("Sprint session not found or expired.");
        }
        if (student.getId() == null || !student.getId().equals(session.studentId())) {
            throw new IllegalArgumentException("Sprint session does not belong to this user.");
        }
        return session;
    }

    private Map<String, Object> questionPayload(SprintQuestion question) {
        return Map.of(
                "prompt", question.prompt(),
                "options", question.options());
    }

    private String inferThinkingStyle(boolean roundACorrect, boolean roundBCorrect) {
        if (roundACorrect && !roundBCorrect) {
            return "system1_dominant";
        }
        if (!roundACorrect && roundBCorrect) {
            return "system2_deliberate";
        }
        if (roundACorrect) {
            return "system1_dominant";
        }
        return "balanced_needs_practice";
    }

    private String normalizeEmotion(String emotion) {
        String value = emotion == null ? "" : emotion.trim().toLowerCase();
        return switch (value) {
            case "frustrated", "neutral", "motivated" -> value;
            default -> throw new IllegalArgumentException("Emotion must be one of: frustrated, neutral, motivated");
        };
    }

    private ArrayNode parseEmotionLog(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            if (parsed.isArray()) {
                return (ArrayNode) parsed.deepCopy();
            }
        } catch (Exception e) {
            logger.warn("Failed to parse emotion log JSON; resetting. {}", e.getMessage());
        }
        return objectMapper.createArrayNode();
    }

    private enum SprintStage {
        ROUND_A_PENDING,
        ROUND_B_PENDING
    }

    private record SprintQuestion(String prompt, List<String> options, int correctIndex) {
    }

    private record SprintQuestionPair(SprintQuestion roundA, SprintQuestion roundB) {
    }

    private record CognitiveSprintSession(
            String sprintId,
            Long studentId,
            LocalDateTime roundAStart,
            Integer roundAAnswerIndex,
            LocalDateTime roundAAnsweredAt,
            Boolean roundACorrect,
            LocalDateTime roundBStart,
            SprintQuestionPair questionPair,
            SprintStage stage) {

        private CognitiveSprintSession withRoundA(int answerIndex, LocalDateTime answeredAt, boolean correct) {
            return new CognitiveSprintSession(
                    sprintId,
                    studentId,
                    roundAStart,
                    answerIndex,
                    answeredAt,
                    correct,
                    answeredAt,
                    questionPair,
                    SprintStage.ROUND_B_PENDING);
        }
    }
}
