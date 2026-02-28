package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.RishiIntegrationStatusResponse;
import com.skilltracker.student_skill_tracker.dto.RishiModeRequest;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthCompleteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthUrlResponse;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleRequest;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleResponse;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.RishiIntegrationService;

@RestController
@RequestMapping("/api/rishi/integrations")
public class RishiIntegrationController {

    private final StudentRepository studentRepository;
    private final RishiIntegrationService rishiIntegrationService;

    public RishiIntegrationController(
            StudentRepository studentRepository,
            RishiIntegrationService rishiIntegrationService) {
        this.studentRepository = studentRepository;
        this.rishiIntegrationService = rishiIntegrationService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        RishiIntegrationStatusResponse response = rishiIntegrationService.getStatus(studentOpt.get());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mode")
    public ResponseEntity<?> setMode(Authentication auth, @RequestBody(required = false) RishiModeRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        String mode = rishiIntegrationService.setMode(studentOpt.get(), request == null ? null : request.getMode());
        return ResponseEntity.ok(Map.of("mode", mode));
    }

    @GetMapping("/google-calendar/auth-url")
    public ResponseEntity<?> getGoogleAuthUrl(
            Authentication auth,
            @RequestParam("redirectUri") String redirectUri) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            RishiOAuthUrlResponse response = rishiIntegrationService.createGoogleAuthUrl(studentOpt.get(), redirectUri);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/oauth/complete")
    public ResponseEntity<?> completeOAuth(
            Authentication auth,
            @RequestBody(required = false) RishiOAuthCompleteRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            String provider = rishiIntegrationService.completeOAuth(studentOpt.get(), request);
            RishiIntegrationStatusResponse status = rishiIntegrationService.getStatus(studentOpt.get());
            return ResponseEntity.ok(Map.of("provider", provider, "status", status));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/google-calendar/schedule")
    public ResponseEntity<?> scheduleCalendar(
            Authentication auth,
            @RequestBody(required = false) RishiScheduleRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            RishiScheduleResponse response = rishiIntegrationService.scheduleNextBlocks(studentOpt.get(), request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> getTasks(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        return ResponseEntity.ok(rishiIntegrationService.getTasks(studentOpt.get()));
    }

    @PostMapping("/tasks/{taskId}/done")
    public ResponseEntity<?> markTaskDone(Authentication auth, @PathVariable Long taskId) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            rishiIntegrationService.markTaskDone(studentOpt.get(), taskId);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/google-calendar/disconnect")
    public ResponseEntity<?> disconnectGoogleCalendar(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        rishiIntegrationService.disconnectGoogleCalendar(studentOpt.get());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private Optional<Student> getCurrentStudent(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return Optional.empty();
        }
        return studentRepository.findByEmailIgnoreCase(auth.getName());
    }
}
