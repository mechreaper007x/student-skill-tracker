package com.skilltracker.student_skill_tracker.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skilltracker.student_skill_tracker.model.DojoPuzzle;
import com.skilltracker.student_skill_tracker.repository.DojoPuzzleRepository;

@Service
public class DuelGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DuelGenerationService.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    // Round-robin model rotation to distribute load and avoid per-model rate limits
    private static final List<String> MODELS = List.of(
            "gemini-2.5-flash",
            "gemma-3-12b-it",
            "gemma-3-1b-it",
            "gemma-3-4b-it",
            "gemma-3-2b-it",
            "gemma-3-27b-it",
            "gemini-2.0-flash");
    private final AtomicInteger modelIndex = new AtomicInteger(0);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DojoPuzzleRepository dojoPuzzleRepository;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    public DuelGenerationService(RestTemplate restTemplate, ObjectMapper objectMapper,
            DojoPuzzleRepository dojoPuzzleRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.dojoPuzzleRepository = dojoPuzzleRepository;
    }

    @Async
    public void generateAndCachePuzzleAsync() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("Cannot generate DojoPuzzle: gemini.api.key is missing in application.properties");
            return;
        }

        log.info("Generating a new 5-round DojoPuzzle via Gemini API...");

        String model = getNextModel();
        log.info("Using model: {}", model);

        String prompt = buildFiveRoundPrompt();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String url = GEMINI_BASE_URL + model + ":generateContent?key=" + geminiApiKey;

            String responseRaw = restTemplate.postForObject(url, request, String.class);
            JsonNode root = objectMapper.readTree(responseRaw);
            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");

            if (textNode.isMissingNode()) {
                log.error("Failed to parse Gemini response");
                return;
            }

            String jsonPayload = cleanMarkdownWrapper(textNode.asText().trim());
            JsonNode puzzleData = objectMapper.readTree(jsonPayload);

            // Extract the rounds array
            JsonNode roundsNode = puzzleData.path("rounds");
            if (roundsNode.isMissingNode() || !roundsNode.isArray() || roundsNode.size() < 5) {
                log.error("Gemini response missing valid 'rounds' array (got {} elements)",
                        roundsNode.isArray() ? roundsNode.size() : 0);
                return;
            }
            ArrayNode sanitizedRoundsNode = sanitizeRounds(roundsNode);

            // Extract Round 5 (coding) for backward-compatible fields
            JsonNode codingRound = null;
            for (JsonNode round : sanitizedRoundsNode) {
                if ("CODING".equalsIgnoreCase(round.path("type").asText())) {
                    codingRound = round;
                    break;
                }
            }

            DojoPuzzle.DojoPuzzleBuilder builder = DojoPuzzle.builder()
                    .title(puzzleData.path("title").asText("Generated 5-Round Duel"))
                    .descriptionHtml(puzzleData.path("descriptionHtml")
                            .asText("<p>A 5-round competitive duel challenge</p>"))
                    .difficulty(puzzleData.path("difficulty").asText("Mixed"))
                    .roundsJson(sanitizedRoundsNode.toString())
                    .isGeneratedByAi(true);

            // Populate coding fields from Round 5 for backward compat
            if (codingRound != null) {
                JsonNode starterCode = codingRound.path("starterCode");
                builder.starterCodeJava(starterCode.path("java").asText(""))
                        .starterCodePython(starterCode.path("python").asText(""))
                        .starterCodeCpp(starterCode.path("cpp").asText(""))
                        .starterCodeJavascript(starterCode.path("javascript").asText(""))
                        .hiddenTestCasesJson(codingRound.path("hiddenTestCases").toString());
            }

            DojoPuzzle puzzle = builder.build();
            dojoPuzzleRepository.save(puzzle);
            log.info("Successfully generated and cached 5-round DojoPuzzle: {}", puzzle.getTitle());

        } catch (Exception e) {
            log.error("Failed to generate and cache 5-round DojoPuzzle", e);
        }
    }

    private String buildFiveRoundPrompt() {
        return """
                You are a game master for a competitive 1v1 programming duel arena called "Will to Power".
                Generate a complete 5-round duel challenge set. Each round tests a different skill.
                Return ONLY a raw JSON object (no markdown, no backticks) matching EXACTLY this schema:

                {
                  "title": "Creative duel title",
                  "descriptionHtml": "<p>Brief HTML description of this duel set</p>",
                  "difficulty": "Mixed",
                  "rounds": [
                    {
                      "round": 1,
                      "type": "MCQ",
                      "title": "Round title",
                      "bloomLevel": 2,
                      "bloomLevelName": "Remember & Understand",
                      "timeLimitSeconds": 120,
                      "questions": [
                        {
                          "question": "A computer science / DSA / programming concept question",
                          "options": ["Option A", "Option B", "Option C", "Option D"],
                          "correctIndex": 0
                        }
                      ]
                    },
                    {
                      "round": 2,
                      "type": "MEMORY",
                      "title": "Round title",
                      "bloomLevel": 3,
                      "bloomLevelName": "Understand & Apply",
                      "timeLimitSeconds": 120,
                      "gridSize": 4,
                      "pairs": [
                        {"id": 0, "content": "HashMap", "match": "O(1) lookup"},
                        {"id": 1, "content": "BFS", "match": "Queue"},
                        {"id": 2, "content": "DFS", "match": "Stack"},
                        {"id": 3, "content": "Binary Search", "match": "O(log n)"},
                        {"id": 4, "content": "Merge Sort", "match": "O(n log n)"},
                        {"id": 5, "content": "Linked List", "match": "O(n) access"},
                        {"id": 6, "content": "Heap", "match": "Priority Queue"},
                        {"id": 7, "content": "Array", "match": "O(1) access"}
                      ]
                    },
                    {
                      "round": 3,
                      "type": "PUZZLE",
                      "title": "Round title",
                      "bloomLevel": 4,
                      "bloomLevelName": "Analyze",
                      "timeLimitSeconds": 180,
                      "puzzleHtml": "<p>HTML formatted logic puzzle (find pattern, decode, sequence, etc)</p>",
                      "answer": "the correct answer as a string",
                      "hints": ["Hint 1", "Hint 2"]
                    },
                    {
                      "round": 4,
                      "type": "PROBLEM_SOLVING",
                      "title": "Round title",
                      "bloomLevel": 5,
                      "bloomLevelName": "Evaluate",
                      "timeLimitSeconds": 180,
                      "problemHtml": "<p>HTML formatted math/logic word problem that does NOT require code</p>",
                      "answer": "the correct answer as a string",
                      "solutionExplanation": "Step-by-step explanation"
                    },
                    {
                      "round": 5,
                      "type": "CODING",
                      "title": "Coding problem title",
                      "bloomLevel": 6,
                      "bloomLevelName": "Apply & Create",
                      "timeLimitSeconds": 600,
                      "descriptionHtml": "<p>HTML formatted beginner-friendly coding problem with examples</p>",
                      "starterCode": {
                        "java": "class Solution {\\n  public int solve(int[] nums) {\\n    return 0;\\n  }\\n}",
                        "python": "def solve(nums):\\n    return 0",
                        "cpp": "#include <vector>\\nusing namespace std;\\n\\nint solve(vector<int> nums) {\\n  return 0;\\n}",
                        "javascript": "function solve(nums) {\\n  return 0;\\n}"
                      },
                      "hiddenTestCases": [
                        {"input": "args", "expectedOutput": "result"},
                        {"input": "args2", "expectedOutput": "result2"}
                      ]
                    }
                  ]
                }

                RULES:
                - Round 1 MCQ: Generate exactly 4 questions about CS fundamentals, data structures, algorithms, or programming concepts. Vary difficulty slightly.
                - Round 2 MEMORY: Generate exactly 8 pairs. Each pair has a CS concept and its matching definition/property. Players must match them from a shuffled grid.
                - Round 3 PUZZLE: Create a logic/pattern puzzle (number sequences, code output prediction, cipher decoding). Must have a single correct text answer.
                - Round 4 PROBLEM_SOLVING: Create a math/logic word problem (no code needed). Think classic interview brain teasers or applied math.
                - Round 5 CODING: Create a very beginner-friendly coding problem (array sum, string reversal, basic loops). Include 3+ hidden test cases. Time limit is 10 minutes.
                - All content should be educational, fun, and have a slightly intense/competitive tone.
                - Use HTML tags (<p>, <b>, <code>, <ul>, <li>) for formatted text, NOT markdown.
                - Keep Bloom metadata exactly aligned to round order:
                  Round 1 -> bloomLevel 2 ("Remember & Understand")
                  Round 2 -> bloomLevel 3 ("Understand & Apply")
                  Round 3 -> bloomLevel 4 ("Analyze")
                  Round 4 -> bloomLevel 5 ("Evaluate")
                  Round 5 -> bloomLevel 6 ("Apply & Create")
                """;
    }

    /**
     * Round-robin model selection: each call returns the next model in the list.
     * Thread-safe via AtomicInteger.
     */
    private String getNextModel() {
        int idx = modelIndex.getAndUpdate(i -> (i + 1) % MODELS.size());
        return MODELS.get(idx);
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

    private ArrayNode sanitizeRounds(JsonNode roundsNode) {
        ArrayNode sanitized = objectMapper.createArrayNode();
        for (JsonNode roundNode : roundsNode) {
            JsonNode copied = roundNode.deepCopy();
            if (copied instanceof ObjectNode roundObject) {
                String type = roundObject.path("type").asText("");
                ensureBloomMetadata(roundObject);
                if ("CODING".equalsIgnoreCase(type)) {
                    sanitizeCodingRound(roundObject);
                }
            }
            sanitized.add(copied);
        }
        return sanitized;
    }

    private void sanitizeCodingRound(ObjectNode codingRound) {
        JsonNode starterNode = codingRound.path("starterCode");
        ObjectNode starterCode = starterNode != null && starterNode.isObject()
                ? (ObjectNode) starterNode.deepCopy()
                : objectMapper.createObjectNode();

        starterCode.put("java", sanitizeStarterCode(starterCode.path("java").asText(""), "java"));
        starterCode.put("python", sanitizeStarterCode(starterCode.path("python").asText(""), "python"));
        starterCode.put("cpp", sanitizeStarterCode(starterCode.path("cpp").asText(""), "cpp"));
        starterCode.put("javascript", sanitizeStarterCode(starterCode.path("javascript").asText(""), "javascript"));

        codingRound.set("starterCode", starterCode);

        JsonNode hiddenTests = codingRound.path("hiddenTestCases");
        if (!hiddenTests.isArray() || hiddenTests.size() == 0) {
            codingRound.set("hiddenTestCases", defaultHiddenTestCases());
        }
    }

    private String sanitizeStarterCode(String candidate, String language) {
        String trimmed = candidate == null ? "" : candidate.trim();
        if (trimmed.isBlank() || isPlaceholderStarterCode(trimmed)) {
            return getDefaultStarterCode(language);
        }
        return trimmed;
    }

    private boolean isPlaceholderStarterCode(String code) {
        String normalized = code.toLowerCase().replaceAll("\\s+", " ").trim();
        boolean containsPlaceholderEllipsis = code.matches("(?s).*\\.\\.\\.(?!\\w).*");
        return normalized.equals("...")
                || containsPlaceholderEllipsis
                || normalized.contains("class solution { ... }")
                || normalized.contains("def solve(...): ...")
                || normalized.contains("function solve(...) { ... }")
                || normalized.contains("#include <vector>\\nusing namespace std;\\n...");
    }

    private String getDefaultStarterCode(String language) {
        return switch (language) {
            case "python" -> "def solve(nums):\n    return sum(nums)";
            case "cpp" ->
                "#include <vector>\nusing namespace std;\n\nint solve(vector<int> nums) {\n    int sum = 0;\n    for (int n : nums) {\n        sum += n;\n    }\n    return sum;\n}";
            case "javascript" -> "function solve(nums) {\n  return nums.reduce((sum, n) => sum + n, 0);\n}";
            case "java" -> "class Solution {\n  public int solve(int[] nums) {\n    int sum = 0;\n    for (int n : nums) {\n      sum += n;\n    }\n    return sum;\n  }\n}";
            default -> "class Solution {\n  public int solve(int[] nums) {\n    return 0;\n  }\n}";
        };
    }

    private void ensureBloomMetadata(ObjectNode roundObject) {
        int roundNumber = roundObject.path("round").asInt(0);
        int defaultLevel = defaultBloomLevel(roundNumber);
        String defaultLevelName = defaultBloomLevelName(roundNumber);

        int bloomLevel = roundObject.path("bloomLevel").asInt(defaultLevel);
        if (bloomLevel <= 0) {
            bloomLevel = defaultLevel;
        }

        String bloomLevelName = roundObject.path("bloomLevelName").asText("").trim();
        if (bloomLevelName.isBlank()) {
            bloomLevelName = defaultLevelName;
        }

        roundObject.put("bloomLevel", bloomLevel);
        roundObject.put("bloomLevelName", bloomLevelName);
    }

    private int defaultBloomLevel(int roundNumber) {
        return switch (roundNumber) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            case 4 -> 5;
            case 5 -> 6;
            default -> 1;
        };
    }

    private String defaultBloomLevelName(int roundNumber) {
        return switch (roundNumber) {
            case 1 -> "Remember & Understand";
            case 2 -> "Understand & Apply";
            case 3 -> "Analyze";
            case 4 -> "Evaluate";
            case 5 -> "Apply & Create";
            default -> "Remember";
        };
    }

    private ArrayNode defaultHiddenTestCases() {
        ArrayNode hiddenTests = objectMapper.createArrayNode();
        hiddenTests.add(objectMapper.createObjectNode().put("input", "[1,2,3]").put("expectedOutput", "6"));
        hiddenTests.add(objectMapper.createObjectNode().put("input", "[5]").put("expectedOutput", "5"));
        hiddenTests.add(objectMapper.createObjectNode().put("input", "[-1,1,0]").put("expectedOutput", "0"));
        return hiddenTests;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void ensurePuzzleReservoir() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return;
        }
        long unusedCount = dojoPuzzleRepository.countByUsageCount(0);
        if (unusedCount < 5) {
            log.info("Puzzle reservoir low ({} unused). Triggering background generation...", unusedCount);
            generateAndCachePuzzleAsync();
        }
    }
}
