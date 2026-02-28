package com.skilltracker.student_skill_tracker.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.compiler.CompilationResult;
import com.skilltracker.student_skill_tracker.compiler.CompilerFactory;
import com.skilltracker.student_skill_tracker.compiler.CompilerInfo;
import com.skilltracker.student_skill_tracker.dto.CodeExecutionRequest;
import com.skilltracker.student_skill_tracker.dto.LeetCodeAuthConnectRequest;
import com.skilltracker.student_skill_tracker.dto.LeetCodeSubmissionRequest;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.CognitiveMetricService;
import com.skilltracker.student_skill_tracker.service.LeetCodeService;
import com.skilltracker.student_skill_tracker.service.TokenCryptoService;

@RestController
@RequestMapping("/api/compiler")
public class CompilerController {

    private static final Logger logger = LoggerFactory.getLogger(CompilerController.class);
    private final LeetCodeService leetCodeService;
    private final StudentRepository studentRepository;
    private final TokenCryptoService tokenCryptoService;
    private final CognitiveMetricService cognitiveMetricService;

    public CompilerController(
            LeetCodeService leetCodeService,
            StudentRepository studentRepository,
            TokenCryptoService tokenCryptoService,
            CognitiveMetricService cognitiveMetricService) {
        this.leetCodeService = leetCodeService;
        this.studentRepository = studentRepository;
        this.tokenCryptoService = tokenCryptoService;
        this.cognitiveMetricService = cognitiveMetricService;
    }

    /**
     * Execute code in the specified language.
     * Protected by JWT via SecurityConfig (anyRequest().authenticated()).
     */
    @PostMapping("/execute")
    public ResponseEntity<?> executeCode(@RequestBody CodeExecutionRequest request, Authentication authentication) {
        logger.info("Code execution request: language={}, timeout={}s",
                request.getLanguage(), request.getTimeoutSeconds());

        Optional<Student> studentOpt = getCurrentStudent(authentication);
        studentOpt.ifPresent(student -> {
            cognitiveMetricService.trackPlanningTime(student, request.getProblemSlug());
            cognitiveMetricService.trackRecoveryVelocity(student);
        });

        // Validate language
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Language is required"));
        }

        if (request.getSourceCode() == null || request.getSourceCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Source code cannot be empty"));
        }

        // Check if language is supported and available
        if (!CompilerFactory.isLanguageSupported(request.getLanguage())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unsupported or unavailable language: " + request.getLanguage(),
                    "availableLanguages", CompilerFactory.getAvailableCompilers()));
        }

        // Enforce timeout limits (min 1s, max 30s)
        int timeout = Math.max(1, Math.min(30, request.getTimeoutSeconds()));

        try {
            long startTime = System.currentTimeMillis();

            CompilationResult result = CompilerFactory
                    .getCompiler(request.getLanguage())
                    .executeCode(request.getSourceCode(), request.getInput(), timeout);

            long elapsed = System.currentTimeMillis() - startTime;
            result.setExecutionTime(elapsed + "ms");
            result.setLanguage(request.getLanguage());
            result.setTimestamp(LocalDateTime.now());

            logger.info("Execution complete: success={}, time={}ms", result.isSuccess(), elapsed);

            studentOpt.ifPresent(student -> {
                cognitiveMetricService.recordCompilation(student, result.isSuccess());
            });

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Execution failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Execution failed: " + e.getMessage()));
        }
    }

    /**
     * Get list of available compilers/interpreters on this server.
     */
    @GetMapping("/languages")
    public ResponseEntity<List<CompilerInfo>> getAvailableLanguages() {
        return ResponseEntity.ok(CompilerFactory.getAvailableCompilers());
    }

    @GetMapping("/leetcode/auth-status")
    public ResponseEntity<?> getLeetCodeAuthStatus(Authentication authentication) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("connected", false));
        }
        return ResponseEntity.ok(Map.of("connected", studentOpt.get().hasLeetCodeSubmitAuth()));
    }

    @PostMapping("/leetcode/connect")
    public ResponseEntity<?> connectLeetCodeAuth(
            @RequestBody LeetCodeAuthConnectRequest request,
            Authentication authentication) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        if (request.getLeetcodeSession() == null || request.getLeetcodeSession().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "LeetCode session token is required"));
        }

        if (request.getCsrfToken() == null || request.getCsrfToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "LeetCode CSRF token is required"));
        }

        Student student = studentOpt.get();
        student.setLeetcodeSessionEncrypted(tokenCryptoService.encrypt(request.getLeetcodeSession().trim()));
        student.setLeetcodeCsrfTokenEncrypted(tokenCryptoService.encrypt(request.getCsrfToken().trim()));
        studentRepository.save(student);

        return ResponseEntity.ok(Map.of(
                "connected", true,
                "message", "LeetCode auth linked."));
    }

    @DeleteMapping("/leetcode/connect")
    public ResponseEntity<?> disconnectLeetCodeAuth(Authentication authentication) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Student student = studentOpt.get();
        student.setLeetcodeSessionEncrypted(null);
        student.setLeetcodeCsrfTokenEncrypted(null);
        studentRepository.save(student);

        return ResponseEntity.ok(Map.of(
                "connected", false,
                "message", "LeetCode auth disconnected."));
    }

    /**
     * Submit code directly to LeetCode for the selected problem.
     * Uses the authenticated user's connected LeetCode session.
     */
    @PostMapping("/leetcode/submit")
    public ResponseEntity<?> submitToLeetCode(
            @RequestBody LeetCodeSubmissionRequest request,
            Authentication authentication) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        if (request.getProblemSlug() == null || request.getProblemSlug().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Problem slug is required"));
        }

        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Language is required"));
        }

        if (request.getSourceCode() == null || request.getSourceCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Source code cannot be empty"));
        }

        Student student = studentOpt.get();
        if (!student.hasLeetCodeSubmitAuth()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "LeetCode is not connected. Link your session once and retry."));
        }

        String leetCodeSession = tokenCryptoService.decrypt(student.getLeetcodeSessionEncrypted());
        String csrfToken = tokenCryptoService.decrypt(student.getLeetcodeCsrfTokenEncrypted());
        if (leetCodeSession.isBlank() || csrfToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Stored LeetCode auth is invalid. Reconnect and retry."));
        }

        try {
            Map<String, Object> submissionResult = leetCodeService.submitCodeToLeetCode(
                    request.getProblemSlug(),
                    request.getLanguage(),
                    request.getSourceCode(),
                    leetCodeSession,
                    csrfToken,
                    request.isWaitForResult());

            boolean accepted = "Accepted".equalsIgnoreCase(String.valueOf(submissionResult.get("status")));
            submissionResult.put("emotionCheckRequired", !accepted);
            cognitiveMetricService.recordSubmission(student, request.getProblemSlug(), accepted);
            if (!accepted) {
                // Logic for recovery velocity will trigger on the NEXT successful action
            }

            return ResponseEntity.ok(submissionResult);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("LeetCode submission failed for problem {}", request.getProblemSlug(), ex);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "LeetCode submission failed",
                    "details", ex.getMessage()));
        }
    }

    @PostMapping("/leetcode/question-details")
    public ResponseEntity<?> getQuestionDetails(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String slug = request.get("slug");
        String title = request.get("title");
        String url = request.get("url");
        if ((slug == null || slug.isBlank()) && (title == null || title.isBlank()) && (url == null || url.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slug, title, or URL is required"));
        }

        Optional<Student> studentOpt = getCurrentStudent(authentication);
        studentOpt.ifPresent(student -> {
            if (slug != null && !slug.isBlank()) {
                cognitiveMetricService.recordQuestionSelection(student, slug);
            } else {
                cognitiveMetricService.recordQuestionSelection(student, title);
            }
        });

        return leetCodeService.fetchQuestionDetails(slug, title, url);
    }

    private Optional<Student> getCurrentStudent(Authentication authentication) {
        Authentication auth = authentication;
        if (auth == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }

        String email = auth != null ? auth.getName() : null;
        if (email == null || "anonymousUser".equalsIgnoreCase(email)) {
            return Optional.empty();
        }
        return studentRepository.findByEmailIgnoreCase(email);
    }
}
