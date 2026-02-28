package com.skilltracker.student_skill_tracker.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final StudentRepository studentRepository;

    public AdminController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @GetMapping("/export/research-data")
    // @PreAuthorize("hasRole('ADMIN')") // Note: Ensure method security is enabled in SecurityConfig for this to work
    public ResponseEntity<List<Map<String, Object>>> exportResearchData() {
        List<Student> students = studentRepository.findAll();
        
        List<Map<String, Object>> exportData = students.stream().map(student -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("studentId", student.getId());
            map.put("email", student.getEmail());
            map.put("level", student.getLevel() != null ? student.getLevel() : 1);
            map.put("xp", student.getXp() != null ? student.getXp() : 0);
            map.put("highestBloomLevel", student.getHighestBloomLevel() != null ? student.getHighestBloomLevel() : 1);
            map.put("thinkingStyle", student.getThinkingStyle() != null ? student.getThinkingStyle() : "unknown");
            map.put("totalSubmissions", student.getTotalSubmissions());
            map.put("acceptedSubmissions", student.getAcceptedSubmissions());
            map.put("avgPlanningTimeMs", student.getAvgPlanningTimeMs());
            map.put("avgRecoveryVelocityMs", student.getAvgRecoveryVelocityMs());
            map.put("emotionLog", student.getEmotionLogJson() != null ? student.getEmotionLogJson() : "[]");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(exportData);
    }
}
