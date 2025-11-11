package com.skilltracker.student_skill_tracker.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.AdvisorResult;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.AiAdvisorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class AdvisorController {

    private final AiAdvisorService advisor;
    private final SkillDataRepository skillRepo;
    private final StudentRepository studentRepo;

    @GetMapping("/me")
    public ResponseEntity<AdvisorResult> myAdvice(Authentication auth) {
        String email = auth.getName();
        Optional<Student> s = studentRepo.findByEmail(email);
        if (s.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        SkillData latest = skillRepo.findTopByStudentOrderByCreatedAtDesc(s.get()).orElse(null);
        AdvisorResult adv = advisor.advise(latest);
        return ResponseEntity.ok(adv);
    }

    @GetMapping("/{studentId}")
    public ResponseEntity<AdvisorResult> getAdviceFor(@PathVariable Long studentId) {
        Optional<Student> s = studentRepo.findById(studentId);
        if (s.isEmpty()) return ResponseEntity.notFound().build();
        SkillData latest = skillRepo.findTopByStudentOrderByCreatedAtDesc(s.get()).orElse(null);
        AdvisorResult adv = advisor.advise(latest);
        return ResponseEntity.ok(adv);
    }
}
