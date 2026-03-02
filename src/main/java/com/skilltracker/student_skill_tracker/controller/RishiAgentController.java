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
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.RishiAgentExecuteRequest;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.RishiAsyncAgentService;
import com.skilltracker.student_skill_tracker.service.RishiAsyncAgentService.TaskStatus;

@RestController
@RequestMapping("/api/rishi/agent")
public class RishiAgentController {

    private final StudentRepository studentRepository;
    private final RishiAsyncAgentService rishiAsyncAgentService;

    public RishiAgentController(
            StudentRepository studentRepository,
            RishiAsyncAgentService rishiAsyncAgentService) {
        this.studentRepository = studentRepository;
        this.rishiAsyncAgentService = rishiAsyncAgentService;
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
            String taskId = rishiAsyncAgentService.enqueueTask(studentOpt.get(), request);
            return ResponseEntity.accepted().body(Map.of("taskId", taskId, "status", "PENDING"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(RishiAgentController.class)
                    .error("Agent execute failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Agent request failed: " + ex.getMessage()));
        }
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        TaskStatus status = rishiAsyncAgentService.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Task not found", "status", "NOT_FOUND"));
        }

        return ResponseEntity.ok(Map.of(
                "status", status.getStatus(),
                "response", status.getResponse() != null ? status.getResponse() : Map.of(),
                "error", status.getErrorMessage() != null ? status.getErrorMessage() : ""));
    }

    private Optional<Student> getCurrentStudent(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return Optional.empty();
        }
        return studentRepository.findByEmailIgnoreCase(auth.getName());
    }
}
