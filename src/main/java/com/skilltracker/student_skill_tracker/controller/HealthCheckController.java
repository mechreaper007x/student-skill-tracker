package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    @GetMapping("/api/ping")
    public Map<String, String> ping() {
        return Map.of("status", "UP", "message", "Student Skill Tracker is alive");
    }
}
