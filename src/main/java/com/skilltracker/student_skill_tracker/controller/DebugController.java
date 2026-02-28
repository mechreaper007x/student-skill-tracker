package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import com.skilltracker.student_skill_tracker.service.DuelService;

@RestController
public class DebugController {

    private final DuelService duelService;

    public DebugController(DuelService duelService) {
        this.duelService = duelService;
    }

    @GetMapping("/api/debug/ping")
    public Map<String, String> ping() {
        return Map.of("status", "pong", "time", java.time.LocalDateTime.now().toString());
    }

    @GetMapping("/api/debug/force-match")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> forceMatch(@RequestParam String p1, @RequestParam String p2) {
        try {
            duelService.startDuel(p1, p2);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Duel forcefully started between " + p1 + " and " + p2
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}
