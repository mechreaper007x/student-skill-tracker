package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.RishiAgentExecuteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiAgentExecuteResponse;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.RishiAgentService;

@RestController
@RequestMapping("/api/rishi/agent")
public class RishiAgentController {

    private final StudentRepository studentRepository;
    private final RishiAgentService rishiAgentService;

    public RishiAgentController(
            StudentRepository studentRepository,
            RishiAgentService rishiAgentService) {
        this.studentRepository = studentRepository;
        this.rishiAgentService = rishiAgentService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeAgent(
            Authentication auth,
            @RequestBody(required = false) RishiAgentExecuteRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        try {
            RishiAgentExecuteResponse response = rishiAgentService.execute(studentOpt.get(), request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(RishiAgentController.class)
                    .error("Agent execute failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Agent request failed: " + ex.getMessage()));
        }
    }

    private Optional<Student> getCurrentStudent(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return Optional.empty();
        }
        return studentRepository.findByEmailIgnoreCase(auth.getName());
    }
}
