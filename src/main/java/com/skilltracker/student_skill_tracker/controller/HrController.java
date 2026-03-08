package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.InterviewerFeedbackRequestDto;
import com.skilltracker.student_skill_tracker.service.HrInsightsService;

@RestController
public class HrController {

    private final HrInsightsService hrInsightsService;

    public HrController(HrInsightsService hrInsightsService) {
        this.hrInsightsService = hrInsightsService;
    }

    @GetMapping("/api/hr/candidates")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER','ADMIN')")
    public ResponseEntity<?> getCandidates(
            @RequestParam(name = "name", required = false) String name) {
        return ResponseEntity.ok(hrInsightsService.getCandidates(name));
    }

    @GetMapping("/api/hr/candidates/{id}/summary")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER','ADMIN')")
    public ResponseEntity<?> getCandidateSummary(@PathVariable("id") Long candidateId) {
        return hrInsightsService.getCandidateSummary(candidateId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Candidate not found")));
    }

    @GetMapping("/api/hr/candidates/{id}/interview-insights")
    @PreAuthorize("hasAnyRole('HR','INTERVIEWER','ADMIN')")
    public ResponseEntity<?> getInterviewInsights(@PathVariable("id") Long candidateId) {
        return hrInsightsService.getInterviewInsights(candidateId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Candidate not found")));
    }

    @PostMapping("/api/interviewer/candidates/{id}/feedback")
    @PreAuthorize("hasAnyRole('INTERVIEWER','HR','ADMIN')")
    public ResponseEntity<?> submitFeedback(
            Authentication authentication,
            @PathVariable("id") Long candidateId,
            @RequestBody InterviewerFeedbackRequestDto request) {
        String interviewerEmail = authentication != null ? authentication.getName() : "unknown";
        return hrInsightsService.submitFeedback(candidateId, interviewerEmail, request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Candidate not found")));
    }
}
