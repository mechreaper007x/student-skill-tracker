package com.skilltracker.student_skill_tracker.controller;

import java.util.Map;

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
        public ResponseEntity<?> getCurrentUser(org.springframework.security.core.Authentication authentication) {
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
                                                "leetcodeUsername", student.getLeetcodeUsername())))
                                .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                                .body(Map.of("authenticated", false)));
        }

        @PostMapping("/login")
        public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
                String email = credentials.get("email");
                String password = credentials.get("password");
                System.out.println("DEBUG: Login attempt for email: " + email);

                try {
                        Authentication authentication = authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(email, password));

                        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                        String token = jwtUtils.generateToken(userDetails);

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
                                        .build());
                } catch (Exception e) {
                        System.out.println("DEBUG: Login failed for: " + email + " Error: " + e.getMessage());
                        throw e;
                }
        }
}
