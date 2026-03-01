package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
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
import com.skilltracker.student_skill_tracker.service.LoginAttemptService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

        private final AuthenticationManager authenticationManager;
        private final JwtUtils jwtUtils;
        private final StudentRepository studentRepository;
        private final LoginAttemptService loginAttemptService;

        public AuthController(AuthenticationManager authenticationManager,
                        JwtUtils jwtUtils,
                        StudentRepository studentRepository,
                        LoginAttemptService loginAttemptService) {
                this.authenticationManager = authenticationManager;
                this.jwtUtils = jwtUtils;
                this.studentRepository = studentRepository;
                this.loginAttemptService = loginAttemptService;
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
                                .map(student -> {
                                        Map<String, Object> body = new LinkedHashMap<>();
                                        body.put("authenticated", true);
                                        body.put("id", student.getId());
                                        body.put("email", student.getEmail());
                                        body.put("name", student.getName());
                                        body.put("leetcodeUsername", student.getLeetcodeUsername());
                                        body.put("codeforcesHandle", student.getCodeforcesHandle());
                                        body.put("leetcodeSubmitConnected", student.hasLeetCodeSubmitAuth());
                                        body.put("level", student.getLevel() != null ? student.getLevel() : 1);
                                        body.put("xp", student.getXp() != null ? student.getXp() : 0);
                                        body.put("duelWins", student.getDuelWins() != null ? student.getDuelWins() : 0);
                                        body.put("highestBloomLevel",
                                                        student.getHighestBloomLevel() != null ? student.getHighestBloomLevel() : 1);
                                        return ResponseEntity.ok(body);
                                })
                                .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("authenticated", false)));
        }

        @PostMapping("/login")
        public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
                String email = credentials.getOrDefault("email", "").trim();
                String password = credentials.get("password");
                String ip = extractClientIp(request);

                if (email.isBlank() || password == null || password.isBlank()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(Map.of("error", "Email and password are required."));
                }

                if (loginAttemptService.isBlocked(ip)) {
                        return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                                        .body(Map.of("error", "Too many login attempts. Please try again in 15 minutes."));
                }

                logger.debug("Login attempt for email {} from {}", email, ip);

                try {
                        Authentication authentication = authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(email, password));

                        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                        
                        // Brute-Force: Reset on success
                        loginAttemptService.loginSucceeded(ip);
                        String token = jwtUtils.generateToken(userDetails);

                        // Fetch student to get additional info
                        Student student = studentRepository.findByEmail(email)
                                        .orElseThrow(() -> new RuntimeException("Student not found"));

                        logger.debug("Login succeeded for {}", email);

                        return ResponseEntity.ok(LoginResponse.builder()
                                        .token(token)
                                        .email(userDetails.getUsername())
                                        .name(student.getName())
                                        .leetcodeUsername(student.getLeetcodeUsername())
                                        .githubUsername(student.getGithubUsername())
                                        .codeforcesHandle(student.getCodeforcesHandle())
                                        .leetcodeSubmitConnected(student.hasLeetCodeSubmitAuth())
                                        .level(student.getLevel() != null ? student.getLevel() : 1)
                                        .xp(student.getXp() != null ? student.getXp() : 0)
                                        .duelWins(student.getDuelWins() != null ? student.getDuelWins() : 0)
                                        .highestBloomLevel(student.getHighestBloomLevel() != null ? student.getHighestBloomLevel() : 1)
                                        .build());
                } catch (AuthenticationException authException) {
                        logger.warn("Login failed for {} from {}", email, ip);
                        loginAttemptService.loginFailed(ip);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("error", "Invalid email or password."));
                } catch (Exception e) {
                        logger.error("Unexpected login error for {} from {}", email, ip, e);
                        loginAttemptService.loginFailed(ip);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Login failed. Please try again."));
                }
        }

        private String extractClientIp(HttpServletRequest request) {
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                        return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
        }
}
