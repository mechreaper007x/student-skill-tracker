package com.skilltracker.student_skill_tracker.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.AdvisorResult;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.model.TopicMastery;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.repository.TopicMasteryRepository;
import com.skilltracker.student_skill_tracker.service.AiAdvisorService;
import com.skilltracker.student_skill_tracker.service.AiGatewayClient;
import com.skilltracker.student_skill_tracker.service.AiGatewayDefaults;
import com.skilltracker.student_skill_tracker.service.AiGatewayUrlValidator;
import com.skilltracker.student_skill_tracker.service.AiProviderRegistry;
import com.skilltracker.student_skill_tracker.service.RishiGenAiService;
import com.skilltracker.student_skill_tracker.service.RishiMemoryService;
import com.skilltracker.student_skill_tracker.service.TokenCryptoService;

import lombok.RequiredArgsConstructor;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class AdvisorController {

    private static final Logger logger = LoggerFactory.getLogger(AdvisorController.class);
    private static final int MAX_CONTEXT_MESSAGES = 12;

    private final AiAdvisorService advisor;
    private final SkillDataRepository skillRepo;
    private final StudentRepository studentRepo;
    private final RishiGenAiService rishiGenAiService;
    private final RishiMemoryService rishiMemoryService;
    private final TokenCryptoService tokenCryptoService;
    private final TopicMasteryRepository topicMasteryRepository;
    private final AiGatewayDefaults gatewayDefaults;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiGatewayClient aiGatewayClient;

    @GetMapping("/me")
    public ResponseEntity<AdvisorResult> myAdvice(Authentication auth) {
        Optional<Student> s = studentRepo.findByEmail(auth.getName());
        if (s.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        SkillData latest = skillRepo.findTopByStudentOrderByCreatedAtDesc(s.get()).orElse(null);
        AdvisorResult adv = advisor.advise(latest);
        return ResponseEntity.ok(adv);
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<AdvisorResult> getAdviceFor(@PathVariable Long studentId) {
        Optional<Student> s = studentRepo.findById(studentId);
        if (s.isEmpty())
            return ResponseEntity.notFound().build();
        SkillData latest = skillRepo.findTopByStudentOrderByCreatedAtDesc(s.get()).orElse(null);
        AdvisorResult adv = advisor.advise(latest);
        return ResponseEntity.ok(adv);
    }

    @GetMapping("/me/genai-config")
    public ResponseEntity<?> getMyGenAiConfig(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        String model = isBlank(student.getAiModel()) ? gatewayDefaults.model() : student.getAiModel();
        String baseUrl = isBlank(student.getAiBaseUrl())
                ? gatewayDefaults.baseUrl()
                : AiGatewayUrlValidator.normalizeBaseUrl(student.getAiBaseUrl());
        String provider = aiProviderRegistry.displayName(student.getAiProvider(), baseUrl);

        String maskedApiKey = "";
        boolean hasApiKey = !isBlank(student.getAiApiKeyEncrypted());
        if (hasApiKey) {
            try {
                maskedApiKey = maskKey(tokenCryptoService.decrypt(student.getAiApiKeyEncrypted()));
            } catch (Exception ex) {
                maskedApiKey = "INVALID_KEY";
            }
        }

        return ResponseEntity.ok(Map.of(
                "provider", provider,
                "model", model,
                "baseUrl", baseUrl,
                "hasApiKey", hasApiKey,
                "maskedApiKey", maskedApiKey));
    }

    @GetMapping("/me/genai-models")
    public ResponseEntity<?> listMyGenAiModels(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        if (isBlank(student.getAiApiKeyEncrypted())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Connect an API key before fetching models."));
        }

        try {
            String apiKey = tokenCryptoService.decrypt(student.getAiApiKeyEncrypted());
            String baseUrl = isBlank(student.getAiBaseUrl()) ? gatewayDefaults.baseUrl() : student.getAiBaseUrl();
            // OpenRouter exposes a user-filtered catalogue that respects the
            // account's provider preferences, privacy settings, and guardrails.
            boolean userFilteredCatalog = "openrouter".equalsIgnoreCase(student.getAiProvider());
            List<String> models = aiGatewayClient.listModels(apiKey, baseUrl, userFilteredCatalog);
            String selectedModel = isBlank(student.getAiModel())
                    ? aiProviderRegistry.findById(student.getAiProvider())
                            .map(AiProviderRegistry.ProviderProfile::defaultModel)
                            .orElse(gatewayDefaults.model())
                    : student.getAiModel();

            Map<String, Object> result = new HashMap<>();
            result.put("provider", aiProviderRegistry.displayName(student.getAiProvider(), baseUrl));
            result.put("selectedModel", selectedModel);
            result.put("models", models);

            if (models.isEmpty() && "meta".equalsIgnoreCase(student.getAiProvider())) {
                AiGatewayClient.ModelAvailability availability =
                        aiGatewayClient.checkModelAvailability(apiKey, baseUrl, selectedModel);
                result.put("catalogStatus", "empty");
                result.put("selectedModelAvailability", availability.name().toLowerCase());
                result.put("catalogMessage", switch (availability) {
                    case AVAILABLE -> "Meta returned an empty model catalogue, but the selected model is independently confirmed as available. This points to a catalogue response issue, not an invalid API key.";
                    case UNAUTHORIZED -> "Meta returned an empty catalogue and rejected the selected-model check with 401. Check that the saved key is active and copied in full.";
                    case FORBIDDEN -> "Meta returned an empty catalogue and denied access to the selected model. Check your Meta team permissions and model access.";
                    case NOT_FOUND -> "Meta returned an empty catalogue and could not find the selected model for this key.";
                    case UPSTREAM_ERROR -> "Meta returned an empty catalogue and the selected-model check could not be completed. Retry; the provider may be temporarily unable to resolve its model registry.";
                });
            } else {
                result.put("catalogStatus", models.isEmpty() ? "empty" : "available");
                result.put("selectedModelAvailability", "not_checked");
                result.put("catalogMessage", models.isEmpty()
                        ? "The configured API returned an empty model catalogue. You can still enter a model ID manually."
                        : "Model IDs were returned by the configured API.");
            }
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            logger.warn("Rishi model discovery failed for {}: {}", auth.getName(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error",
                    "Could not fetch the model list from this API. You can still enter a model ID manually."));
        }
    }

    @PostMapping("/me/genai-config")
    public ResponseEntity<?> saveMyGenAiConfig(Authentication auth, @RequestBody Map<String, String> payload) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        String apiKey = payload.get("apiKey");
        boolean replacingKey = !isBlank(apiKey);
        String providerId = student.getAiProvider();
        String rawBaseUrl = payload.get("baseUrl");
        String model = payload.get("model");

        AiProviderRegistry.ProviderProfile detectedProfile = replacingKey
                ? aiProviderRegistry.detect(apiKey).orElse(null)
                : aiProviderRegistry.findById(providerId).orElse(null);

        if (replacingKey && detectedProfile != null) {
            // Known key prefix: choose a fixed, reviewed endpoint on the server.
            providerId = detectedProfile.id();
            rawBaseUrl = detectedProfile.baseUrl();
            if (isBlank(model)) {
                model = detectedProfile.defaultModel();
            }
        } else {
            if (isBlank(rawBaseUrl)) {
                rawBaseUrl = isBlank(student.getAiBaseUrl())
                        ? gatewayDefaults.baseUrl()
                        : student.getAiBaseUrl();
            }
        }

        if (replacingKey && detectedProfile == null && isBlank(payload.get("baseUrl"))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error",
                    "I couldn't identify this API key's provider prefix. Open Advanced gateway settings and enter its OpenAI-compatible base URL. The key has not been saved."));
        }

        final String baseUrl;
        try {
            baseUrl = AiGatewayUrlValidator.normalizeBaseUrl(rawBaseUrl);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        if (replacingKey && detectedProfile == null) {
            // A new opaque key must never inherit the previous provider's URL or label.
            detectedProfile = aiProviderRegistry.findByBaseUrl(baseUrl).orElse(null);
            providerId = detectedProfile == null ? "custom" : detectedProfile.id();
            if (isBlank(model) && detectedProfile != null) {
                model = detectedProfile.defaultModel();
            }
        } else if (!isBlank(payload.get("baseUrl")) && detectedProfile == null) {
            providerId = aiProviderRegistry.findByBaseUrl(baseUrl)
                    .map(AiProviderRegistry.ProviderProfile::id)
                    .orElse("custom");
        } else if (isBlank(providerId)) {
            providerId = aiProviderRegistry.findByBaseUrl(baseUrl)
                    .map(AiProviderRegistry.ProviderProfile::id)
                    .orElse("custom");
        }
        if (isBlank(model)) {
            model = isBlank(student.getAiModel()) ? gatewayDefaults.model() : student.getAiModel();
        }
        if (isBlank(model)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Model ID is required for this custom gateway."));
        }
        if (model.trim().length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Model ID must be 200 characters or fewer."));
        }

        if (replacingKey) {
            student.setAiApiKeyEncrypted(tokenCryptoService.encrypt(apiKey.trim()));
        } else if (isBlank(student.getAiApiKeyEncrypted())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Gateway API key is required."));
        }

        student.setAiProvider(isBlank(providerId) ? "custom" : providerId);
        student.setAiModel(model.trim());
        student.setAiBaseUrl(baseUrl);
        studentRepo.save(student);

        String maskedApiKey = maskKey(tokenCryptoService.decrypt(student.getAiApiKeyEncrypted()));
        String provider = aiProviderRegistry.displayName(student.getAiProvider(), student.getAiBaseUrl());
        return ResponseEntity.ok(Map.of(
                "message", "AI gateway configuration saved",
                "provider", provider,
                "model", student.getAiModel(),
                "baseUrl", student.getAiBaseUrl(),
                "hasApiKey", true,
                "maskedApiKey", maskedApiKey));
    }

    // Chat History Endpoints
    @GetMapping("/me/threads")
    public ResponseEntity<?> getMyThreads(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        List<RishiMemoryService.ChatThread> threads = rishiMemoryService.getAllThreads(studentOpt.get());
        return ResponseEntity.ok(threads);
    }

    @GetMapping("/me/thread/{threadId}")
    public ResponseEntity<?> getMyThread(Authentication auth, @PathVariable String threadId) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        RishiMemoryService.ChatThread thread = rishiMemoryService.getThread(studentOpt.get(), threadId);
        if (thread == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Thread not found"));
        }
        return ResponseEntity.ok(Map.of(
            "id", thread.id,
            "messages", thread.messages,
            "count", thread.messages.size()
        ));
    }

    @DeleteMapping("/me/thread/{threadId}")
    public ResponseEntity<?> deleteMyThread(Authentication auth, @PathVariable String threadId) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        Student student = studentOpt.get();
        rishiMemoryService.deleteThread(student, threadId);
        studentRepo.save(student);
        return ResponseEntity.ok(Map.of("message", "Thread deleted"));
    }

    @GetMapping("/me/memory")
    public ResponseEntity<?> getMyMemory(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        List<RishiMemoryService.MemoryMessage> messages = rishiMemoryService.getMemory(studentOpt.get());
        return ResponseEntity.ok(Map.of(
                "messages", messages,
                "count", messages.size()));
    }

    @PostMapping("/me/memory/clear")
    public ResponseEntity<?> clearMyMemory(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        rishiMemoryService.clearMemory(student);
        studentRepo.save(student);
        return ResponseEntity.ok(Map.of("message", "Rishi memory cleared"));
    }

    // Study Plan & Learn
    @GetMapping("/me/study-plan")
    public ResponseEntity<?> getMyStudyPlan(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        return ResponseEntity.ok(toStudyPlanResponse(studentOpt.get()));
    }

    @PostMapping("/me/study-plan")
    public ResponseEntity<?> generateStudyPlan(Authentication auth, @RequestBody Map<String, Object> payload) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        String encryptedKey = student.getAiApiKeyEncrypted();
        if (isBlank(encryptedKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "GenAI API key is not configured. Please attach your key first."));
        }

        String apiKey = tokenCryptoService.decrypt(encryptedKey);
        String baseUrl = isBlank(student.getAiBaseUrl()) ? gatewayDefaults.baseUrl() : student.getAiBaseUrl();
        String provider = "gateway";

        String topic = asString(payload.get("topic"));
        if (isBlank(topic)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Topic is required"));
        }

        String goals = asString(payload.get("goals"));
        Integer durationDays = parseDurationDays(payload.get("durationDays"));
        String model = asString(payload.get("model"));
        if (isBlank(model)) {
            model = isBlank(student.getAiModel()) ? gatewayDefaults.model() : student.getAiModel();
        }

        List<RishiMemoryService.MemoryMessage> memory = rishiMemoryService.getMemory(student);
        List<Map<String, String>> context = rishiMemoryService.toModelContext(memory, MAX_CONTEXT_MESSAGES);
        String skillSnapshot = buildSkillSnapshot(student);

        try {
            String plan = rishiGenAiService.generateStudyPlan(apiKey, model, baseUrl, topic, goals, durationDays, skillSnapshot, context);
            student.setRishiStudyPlan(plan);
            student.setRishiStudyTopic(topic.trim());
            student.setRishiStudyDays(durationDays);
            student.setRishiStudyGeneratedAt(LocalDateTime.now());

            String userPromptSummary = "Generate a " + durationDays + "-day study plan for topic: " + topic.trim() + (isBlank(goals) ? "" : ". Goals: " + goals.trim());
            
            // For study plans, we append to the most recent thread or create a new one
            List<RishiMemoryService.ChatThread> threads = rishiMemoryService.getAllThreads(student);
            String threadId = threads.isEmpty() ? null : threads.get(0).id;
            rishiMemoryService.appendExchange(student, threadId, userPromptSummary, "strategy", plan, "strategy");
            studentRepo.save(student);

            Map<String, Object> response = toStudyPlanResponse(student);
            response.put("provider", provider);
            response.put("model", model);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Rishi study plan generation failed for {}: {}", auth.getName(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "AI gateway request failed. Verify the OpenAI-compatible base URL, API key, and model ID."));
        }
    }

    @PostMapping("/me/learn")
    public ResponseEntity<?> learnWithRishi(Authentication auth, @RequestBody Map<String, String> payload) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        String encryptedKey = student.getAiApiKeyEncrypted();
        if (isBlank(encryptedKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "GenAI API key is not configured. Please attach your key first."));
        }

        String apiKey = tokenCryptoService.decrypt(encryptedKey);
        String provider = "gateway";
        String model = payload.get("model");
        if (isBlank(model)) {
            model = isBlank(student.getAiModel()) ? gatewayDefaults.model() : student.getAiModel();
        }

        String message = payload.get("message");
        if (isBlank(message)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
        }

        String responseType = payload.get("type");
        if (isBlank(responseType)) {
            responseType = "text";
        }
        
        String threadId = payload.get("threadId");

        // Fetch context for the specific thread
        List<RishiMemoryService.MemoryMessage> memory = null;
        if (threadId != null && !threadId.isBlank()) {
            RishiMemoryService.ChatThread thread = rishiMemoryService.getThread(student, threadId);
            if (thread != null) {
                memory = thread.messages;
            }
        }
        
        if (memory == null) {
            memory = List.of();
        }

        List<Map<String, String>> context = rishiMemoryService.toModelContext(memory, MAX_CONTEXT_MESSAGES);
        String studentContext = buildSkillSnapshot(student);

        try {
            String baseUrl = isBlank(student.getAiBaseUrl()) ? gatewayDefaults.baseUrl() : student.getAiBaseUrl();
            String reply = rishiGenAiService.generateLearningResponse(apiKey, model, baseUrl, message, context, studentContext);
            String newOrExistingThreadId = rishiMemoryService.appendExchange(student, threadId, message, "text", reply, responseType);
            studentRepo.save(student);

            RishiMemoryService.ChatThread updatedThread = rishiMemoryService.getThread(student, newOrExistingThreadId);
            int count = updatedThread != null ? updatedThread.messages.size() : 0;

            return ResponseEntity.ok(Map.of(
                    "reply", reply,
                    "provider", provider,
                    "model", model,
                    "threadId", newOrExistingThreadId,
                    "memoryCount", count));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (ResourceAccessException ex) {
            logger.warn("Rishi provider request timed out or lost its connection for {}: {}",
                    auth.getName(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(Map.of(
                    "error",
                    "The AI provider request timed out after 120 seconds. This is a generation timeout, not proof that your API key or model ID is invalid. Retry, or choose a faster model."));
        } catch (Exception ex) {
            logger.error("Rishi learn request failed for {}: {}", auth.getName(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "AI gateway request failed. Verify the OpenAI-compatible base URL, API key, and model ID."));
        }
    }

    private Optional<Student> getCurrentStudent(Authentication auth) {
        if (auth == null || isBlank(auth.getName())) {
            return Optional.empty();
        }
        return studentRepo.findByEmailIgnoreCase(auth.getName());
    }

    private Map<String, Object> toStudyPlanResponse(Student student) {
        Map<String, Object> result = new HashMap<>();
        boolean hasPlan = !isBlank(student.getRishiStudyPlan());

        result.put("hasPlan", hasPlan);
        result.put("topic", student.getRishiStudyTopic() == null ? "" : student.getRishiStudyTopic());
        result.put("durationDays", student.getRishiStudyDays() == null ? 0 : student.getRishiStudyDays());
        result.put("generatedAt", student.getRishiStudyGeneratedAt() == null ? "" : student.getRishiStudyGeneratedAt().toString());
        result.put("plan", hasPlan ? student.getRishiStudyPlan() : "");
        return result;
    }

    private String buildSkillSnapshot(Student student) {
        SkillData latest = skillRepo.findTopByStudentOrderByCreatedAtDesc(student).orElse(null);
        String snapshot = "No tracked scores yet.";
        
        if (latest != null) {
            int totalSolved = latest.getTotalProblemsSolved() == null ? 0 : latest.getTotalProblemsSolved();
            snapshot = String.format(
                    "ProblemSolving=%.1f, Algorithms=%.1f, DataStructures=%.1f, TotalSolved=%d, Easy=%d, Medium=%d, Hard=%d",
                    safe(latest.getProblemSolvingScore()),
                    safe(latest.getAlgorithmsScore()),
                    safe(latest.getDataStructuresScore()),
                    totalSolved,
                    latest.getEasyProblems() == null ? 0 : latest.getEasyProblems(),
                    latest.getMediumProblems() == null ? 0 : latest.getMediumProblems(),
                    latest.getHardProblems() == null ? 0 : latest.getHardProblems());
        }

        List<TopicMastery> masteries = topicMasteryRepository.findByStudent(student);
        if (masteries != null && !masteries.isEmpty()) {
            String decayingTopics = masteries.stream()
                .filter(m -> m.getCurrentDecayRate() > 0.3)
                .map(m -> m.getTopicSlug() + " (Decay: " + String.format("%.2f", m.getCurrentDecayRate()) + ")")
                .collect(Collectors.joining(", "));
            
            if (!decayingTopics.isBlank()) {
                snapshot += "\n[CRITICAL SM-2 DECAY - Must Review]: " + decayingTopics;
            }
            
            String stableTopics = masteries.stream()
                .filter(m -> m.getCurrentDecayRate() <= 0.1)
                .map(TopicMastery::getTopicSlug)
                .collect(Collectors.joining(", "));
            
            if (!stableTopics.isBlank()) {
                snapshot += "\n[Stable Topics]: " + stableTopics;
            }
        }
        
        return snapshot;
    }

    private Integer parseDurationDays(Object value) {
        if (value == null) {
            return 7;
        }

        try {
            int parsed;
            if (value instanceof Number n) {
                parsed = n.intValue();
            } else {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            }

            if (parsed < 1) {
                return 7;
            }
            return Math.min(parsed, 60);
        } catch (Exception ex) {
            return 7;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String maskKey(String key) {
        if (isBlank(key)) {
            return "";
        }
        String trimmed = key.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
