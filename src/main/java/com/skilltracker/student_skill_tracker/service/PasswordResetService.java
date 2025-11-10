package com.skilltracker.student_skill_tracker.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.PasswordResetToken;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.PasswordResetTokenRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final StudentRepository studentRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final SecureRandom RNG = new SecureRandom();

    public void requestReset(String email, String appBaseUrl) {
        Optional<Student> opt = studentRepo.findByEmail(email);
        // Always respond the same to prevent email enumeration.
        if (opt.isEmpty()) return;

        Student student = opt.get();

        // Invalidate previous tokens (optional but neat)
        tokenRepo.deleteByStudent_Id(student.getId());

        String token = generateToken();
        PasswordResetToken prt = PasswordResetToken.builder()
                .student(student)
                .token(token)
                .expiresAt(Instant.now().plusSeconds(30 * 60))
                .used(false)
                .build();
        tokenRepo.save(prt);

        String link = appBaseUrl + "/reset-password.html?token=" + token;
        String body = """
                You requested a password reset for Student Skill Tracker.

                Reset link (valid 30 minutes, one-time use):
                %s

                If you didn't request this, ignore this email.
                """.formatted(link);

        emailService.send(student.getEmail(), "Password Reset", body);
    }

    public boolean resetPassword(String token, String newRawPassword) {
        PasswordResetToken prt = tokenRepo.findByToken(token).orElse(null);
        if (prt == null || prt.isUsed() || prt.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        Student s = prt.getStudent();
        s.setPassword(passwordEncoder.encode(newRawPassword));
        studentRepo.save(s);

        prt.setUsed(true);
        tokenRepo.save(prt);
        return true;
    }

    private String generateToken() {
        byte[] bytes = new byte[32]; // 256-bit
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
