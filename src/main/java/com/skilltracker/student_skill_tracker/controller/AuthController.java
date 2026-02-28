package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.LoginResponse;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.security.JwtUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final AuthenticationManager authenticationManager;
        private final JwtUtils jwtUtils;
        private final StudentRepository studentRepository;

        public AuthController(AuthenticationManager authenticationManager,
                        JwtUtils jwtUtils,
                        StudentRepository studentRepository) {
                this.authenticationManager = authenticationManager;
                this.jwtUtils = jwtUtils;
                this.studentRepository = studentRepository;
        }

        /**
         * Public endpoint to check if user is authenticated.
         * Returns user info if authenticated, 401 if not.
         */
        @GetMapping("/me")
        public ResponseEntity<?> getCurrentUser(Authentication authentication) {
                if (authentication == null || !authentication.isAuthenticated()
                                || "anonymousUser".equals(authentication.getPrincipal())) {
                        return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("authenticated", false));
                }

                String email = authentication.getName();
                return studentRepository.findByEmail(email)
                                .map(student -> ResponseEntity.ok(Map.of(
                                                "authenticated", true,
                                                "id", student.getId(),
                                                "email", student.getEmail(),
                                                "name", student.getName(),
                                                "leetcodeUsername", student.getLeetcodeUsername(),
                                                "leetcodeSubmitConnected", student.hasLeetCodeSubmitAuth(),
                                                "level", student.getLevel() != null ? student.getLevel() : 1,
                                                "xp", student.getXp() != null ? student.getXp() : 0,
                                                "duelWins", student.getDuelWins() != null ? student.getDuelWins() : 0,
                                                "highestBloomLevel", student.getHighestBloomLevel() != null ? student.getHighestBloomLevel() : 1)))
                                .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("authenticated", false)));
        }

        @PostMapping("/login")
        public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request, HttpServletResponse response) {
                String email = credentials.get("email");
                String password = credentials.get("password");
                System.out.println("DEBUG: Login attempt for email: " + email);

                try {
                        Authentication authentication = authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(email, password));

                        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                        
                        // Multi-Fingerprinting: Cookie + UserAgent
                        String cookieFgp = jwtUtils.generateSecureFingerprint();
                        String userAgent = request.getHeader("User-Agent");
                        
                        String token = jwtUtils.generateToken(userDetails, cookieFgp, userAgent);

                        // Set HttpOnly Secure Cookie for the FGP
                        ResponseCookie springCookie = ResponseCookie.from("__Secure-Fgp", cookieFgp)
                            .httpOnly(true)
                            .secure(true) // Requires HTTPS in prod
                            .path("/")
                            .sameSite("Strict")
                            .maxAge(86400) // 24 hours
                            .build();
                        response.addHeader(HttpHeaders.SET_COOKIE, springCookie.toString());

                        // Fetch student to get additional info
                        Student student = studentRepository.findByEmail(email)
                                        .orElseThrow(() -> new RuntimeException("Student not found"));

                        System.out.println("DEBUG: Login successful for: " + email);

                        return ResponseEntity.ok(LoginResponse.builder()
                                        .token(token)
                                        .email(userDetails.getUsername())
                                        .name(student.getName())
                                        .leetcodeUsername(student.getLeetcodeUsername())
                                        .githubUsername(student.getGithubUsername())
                                        .leetcodeSubmitConnected(student.hasLeetCodeSubmitAuth())
                                        .level(student.getLevel() != null ? student.getLevel() : 1)
                                        .xp(student.getXp() != null ? student.getXp() : 0)
                                        .duelWins(student.getDuelWins() != null ? student.getDuelWins() : 0)
                                        .highestBloomLevel(student.getHighestBloomLevel() != null ? student.getHighestBloomLevel() : 1)
                                        .build());
                } catch (Exception e) {
                        System.out.println("DEBUG: Login failed for: " + email + " Error: " + e.getMessage());
                        throw e;
                }
        }
}
