package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.service.DuelService;

@RestController
@RequestMapping("/api/duel")
public class DuelController {

    private static final Logger log = LoggerFactory.getLogger(DuelController.class);
    private final DuelService duelService;

    public DuelController(DuelService duelService) {
        this.duelService = duelService;
    }

    @PostMapping("/lobby/join")
    public ResponseEntity<?> joinLobby(Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }
            String username = authentication.getName();
            log.info("User {} requesting to join duel lobby", username);
            duelService.joinLobby(username);
            return ResponseEntity.ok(Map.of("message", "Joined lobby", "status", "WAITING"));
        } catch (Exception e) {
            log.error("Error joining lobby", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/lobby/leave")
    public ResponseEntity<?> leaveLobby(Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }
            String username = authentication.getName();
            log.info("User {} requesting to leave duel lobby", username);
            duelService.leaveLobby(username);
            return ResponseEntity.ok(Map.of("message", "Left lobby"));
        } catch (Exception e) {
            log.error("Error leaving lobby", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
