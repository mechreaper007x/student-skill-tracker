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
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.AiAdvisorService;
import com.skilltracker.student_skill_tracker.service.RishiGenAiService;
import com.skilltracker.student_skill_tracker.service.RishiMemoryService;
import com.skilltracker.student_skill_tracker.service.TokenCryptoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class AdvisorController {

    private static final Logger logger = LoggerFactory.getLogger(AdvisorController.class);
    private static final String DEFAULT_PROVIDER = "mistral";
    private static final String DEFAULT_MODEL = "open-mixtral-8x7b";
    private static final int MAX_CONTEXT_MESSAGES = 12;

    private final AiAdvisorService advisor;
    private final SkillDataRepository skillRepo;
    private final StudentRepository studentRepo;
    private final RishiGenAiService rishiGenAiService;
    private final RishiMemoryService rishiMemoryService;
    private final TokenCryptoService tokenCryptoService;

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
        String provider = isBlank(student.getAiProvider()) ? DEFAULT_PROVIDER : student.getAiProvider();
        String model = isBlank(student.getAiModel()) ? DEFAULT_MODEL : student.getAiModel();
        String encryptedKey = student.getAiApiKeyEncrypted();
        boolean hasApiKey = !isBlank(encryptedKey);
        
        String maskedApiKey = "";
        if (hasApiKey) {
            try {
                String plainKey = tokenCryptoService.decrypt(encryptedKey);
                maskedApiKey = maskKey(plainKey);
            } catch (Exception e) {
                maskedApiKey = "INVALID_KEY";
            }
        }

        return ResponseEntity.ok(Map.of(
                "provider", provider,
                "model", model,
                "hasApiKey", hasApiKey,
                "maskedApiKey", maskedApiKey));
    }

    @PostMapping("/me/genai-config")
    public ResponseEntity<?> saveMyGenAiConfig(Authentication auth, @RequestBody Map<String, String> payload) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();

        String provider = payload.get("provider");
        if (isBlank(provider)) {
            provider = DEFAULT_PROVIDER;
        }

        String model = payload.get("model");
        if (isBlank(model)) {
            model = DEFAULT_MODEL;
        }

        String apiKey = payload.get("apiKey");

        student.setAiProvider(provider.trim().toLowerCase());
        student.setAiModel(model.trim());
        if (!isBlank(apiKey)) {
            student.setAiApiKeyEncrypted(tokenCryptoService.encrypt(apiKey.trim()));
        }
        studentRepo.save(student);

        return ResponseEntity.ok(Map.of(
                "message", "GenAI config saved",
                "provider", student.getAiProvider(),
                "model", student.getAiModel(),
                "hasApiKey", !isBlank(student.getAiApiKeyEncrypted()),
                "maskedApiKey", maskKey(apiKey)));
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
        String provider = isBlank(student.getAiProvider()) ? DEFAULT_PROVIDER : student.getAiProvider();
        if (!DEFAULT_PROVIDER.equalsIgnoreCase(provider)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unsupported provider. Use mistral for now."));
        }

        String topic = asString(payload.get("topic"));
        if (isBlank(topic)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Topic is required"));
        }

        String goals = asString(payload.get("goals"));
        Integer durationDays = parseDurationDays(payload.get("durationDays"));
        String model = asString(payload.get("model"));
        if (isBlank(model)) {
            model = isBlank(student.getAiModel()) ? DEFAULT_MODEL : student.getAiModel();
        }

        List<RishiMemoryService.MemoryMessage> memory = rishiMemoryService.getMemory(student);
        List<Map<String, String>> context = rishiMemoryService.toModelContext(memory, MAX_CONTEXT_MESSAGES);
        String skillSnapshot = buildSkillSnapshot(student);

        try {
            String plan = rishiGenAiService.generateStudyPlan(apiKey, model, topic, goals, durationDays, skillSnapshot, context);
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
            response.put("provider", DEFAULT_PROVIDER);
            response.put("model", model);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Rishi study plan generation failed for {}: {}", auth.getName(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to generate study plan from Mixtral. Check API key/model and retry."));
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
        String provider = isBlank(student.getAiProvider()) ? DEFAULT_PROVIDER : student.getAiProvider();
        if (!DEFAULT_PROVIDER.equalsIgnoreCase(provider)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unsupported provider. Use mistral for now."));
        }

        String model = payload.get("model");
        if (isBlank(model)) {
            model = isBlank(student.getAiModel()) ? DEFAULT_MODEL : student.getAiModel();
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
            String reply = rishiGenAiService.generateLearningResponse(apiKey, model, message, context, studentContext);
            String newOrExistingThreadId = rishiMemoryService.appendExchange(student, threadId, message, "text", reply, responseType);
            studentRepo.save(student);

            RishiMemoryService.ChatThread updatedThread = rishiMemoryService.getThread(student, newOrExistingThreadId);
            int count = updatedThread != null ? updatedThread.messages.size() : 0;

            return ResponseEntity.ok(Map.of(
                    "reply", reply,
                    "provider", DEFAULT_PROVIDER,
                    "model", model,
                    "threadId", newOrExistingThreadId,
                    "memoryCount", count));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Rishi learn request failed for {}: {}", auth.getName(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to get response from Mixtral. Check API key/model and try again."));
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
        if (latest == null) {
            return "No tracked scores yet.";
        }

        int totalSolved = latest.getTotalProblemsSolved() == null ? 0 : latest.getTotalProblemsSolved();
        return String.format(
                "ProblemSolving=%.1f, Algorithms=%.1f, DataStructures=%.1f, TotalSolved=%d, Easy=%d, Medium=%d, Hard=%d",
                safe(latest.getProblemSolvingScore()),
                safe(latest.getAlgorithmsScore()),
                safe(latest.getDataStructuresScore()),
                totalSolved,
                latest.getEasyProblems() == null ? 0 : latest.getEasyProblems(),
                latest.getMediumProblems() == null ? 0 : latest.getMediumProblems(),
                latest.getHardProblems() == null ? 0 : latest.getHardProblems());
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
