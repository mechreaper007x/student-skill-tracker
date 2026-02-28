package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.RishiCodeChangeBatchRequest;
import com.skilltracker.student_skill_tracker.dto.RishiCompileAttemptRequest;
import com.skilltracker.student_skill_tracker.dto.RishiGrowthSummaryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiSessionEndRequest;
import com.skilltracker.student_skill_tracker.dto.RishiSessionStartRequest;
import com.skilltracker.student_skill_tracker.dto.RishiSessionStartResponse;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.RishiCodingTelemetryService;

@RestController
@RequestMapping("/api/rishi/coding")
public class RishiCodingTelemetryController {

    private final StudentRepository studentRepository;
    private final RishiCodingTelemetryService rishiCodingTelemetryService;

    public RishiCodingTelemetryController(
            StudentRepository studentRepository,
            RishiCodingTelemetryService rishiCodingTelemetryService) {
        this.studentRepository = studentRepository;
        this.rishiCodingTelemetryService = rishiCodingTelemetryService;
    }

    @PostMapping("/sessions/start")
    public ResponseEntity<?> startSession(
            Authentication authentication,
            @RequestBody(required = false) RishiSessionStartRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        RishiSessionStartResponse response = rishiCodingTelemetryService.startSession(studentOpt.get(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sessionId}/events")
    public ResponseEntity<?> recordChanges(
            Authentication authentication,
            @PathVariable Long sessionId,
            @RequestBody(required = false) RishiCodeChangeBatchRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            int accepted = rishiCodingTelemetryService.recordChanges(studentOpt.get(), sessionId, request);
            return ResponseEntity.ok(Map.of("acceptedEvents", accepted));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/compile")
    public ResponseEntity<?> recordCompileAttempt(
            Authentication authentication,
            @PathVariable Long sessionId,
            @RequestBody(required = false) RishiCompileAttemptRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            rishiCodingTelemetryService.recordCompileAttempt(studentOpt.get(), sessionId, request);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<?> endSession(
            Authentication authentication,
            @PathVariable Long sessionId,
            @RequestBody(required = false) RishiSessionEndRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            rishiCodingTelemetryService.endSession(studentOpt.get(), sessionId, request);
            return ResponseEntity.ok(Map.of("status", "ended"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/growth-summary")
    public ResponseEntity<?> getGrowthSummary(
            Authentication authentication,
            @RequestParam(name = "days", defaultValue = "14") int days) {
        Optional<Student> studentOpt = getCurrentStudent(authentication);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        RishiGrowthSummaryResponse response = rishiCodingTelemetryService.getGrowthSummary(studentOpt.get(), days);
        return ResponseEntity.ok(response);
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

