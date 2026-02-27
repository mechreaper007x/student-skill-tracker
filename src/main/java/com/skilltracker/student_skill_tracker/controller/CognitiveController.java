package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.CognitiveMetricService;

@RestController
@RequestMapping("/api/cognitive")
public class CognitiveController {

    private final StudentRepository studentRepository;
    private final CognitiveMetricService cognitiveMetricService;

    public CognitiveController(StudentRepository studentRepository, CognitiveMetricService cognitiveMetricService) {
        this.studentRepository = studentRepository;
        this.cognitiveMetricService = cognitiveMetricService;
    }

    @PostMapping("/sprint")
    public ResponseEntity<?> cognitiveSprint(@RequestBody Map<String, Object> payload, Authentication authentication) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Student student = studentOpt.get();
        String action = String.valueOf(payload.getOrDefault("action", "")).trim().toLowerCase();
        try {
            return switch (action) {
                case "start" -> ResponseEntity.ok(cognitiveMetricService.startCognitiveSprint(student));
                case "submit_round_a", "submitrounda", "rounda" -> {
                    String sprintId = asString(payload.get("sprintId"));
                    Integer selectedIndex = asInteger(payload.get("selectedIndex"));
                    yield ResponseEntity.ok(cognitiveMetricService.submitSprintRoundA(student, sprintId, selectedIndex));
                }
                case "submit_round_b", "submitroundb", "roundb" -> {
                    String sprintId = asString(payload.get("sprintId"));
                    Integer selectedIndex = asInteger(payload.get("selectedIndex"));
                    yield ResponseEntity.ok(cognitiveMetricService.submitSprintRoundB(student, sprintId, selectedIndex));
                }
                default -> ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid action. Use one of: start, submit_round_a, submit_round_b"));
            };
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/emotion")
    public ResponseEntity<?> recordEmotion(@RequestBody Map<String, Object> payload, Authentication authentication) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            String emotion = asString(payload.get("emotion"));
            cognitiveMetricService.recordEmotionAfterFailure(studentOpt.get(), emotion);
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
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

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
