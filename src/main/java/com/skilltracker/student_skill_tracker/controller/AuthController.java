package com.skilltracker.student_skill_tracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skilltracker.student_skill_tracker.service.PasswordResetService;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PasswordResetService passwordResetService;

    // Ask for reset (email)
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotReq req,
                                            @RequestHeader(value = "Origin", required = false) String origin) {
        // Build a base URL safely; fall back to localhost:8081 if Origin missing
        String base = (origin != null && !origin.isBlank()) ? origin : "http://localhost:8081";
        passwordResetService.requestReset(req.getEmail(), base);
        // Always 200 to avoid user enumeration
        return ResponseEntity.ok().body(new Msg("If that email exists, a reset link has been sent."));
    }

    // Perform reset with token
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetReq req) {
        boolean ok = passwordResetService.resetPassword(req.getToken(), req.getNewPassword());
        if (!ok) return ResponseEntity.badRequest().body(new Msg("Invalid or expired token."));
        return ResponseEntity.ok(new Msg("Password updated. You can log in now."));
    }

    @Data
    public static class ForgotReq { private String email; }

    @Data
    public static class ResetReq {
        private String token;
        @JsonProperty("newPassword")
        private String newPassword;
    }

    @Data
    public static class Msg { private final String message; }
}
