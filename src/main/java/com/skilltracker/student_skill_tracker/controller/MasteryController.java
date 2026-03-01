package com.skilltracker.student_skill_tracker.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.repository.TopicMasteryRepository;

@RestController
@RequestMapping("/api/mastery")
public class MasteryController {

    private final StudentRepository studentRepository;
    private final TopicMasteryRepository topicMasteryRepository;

    public MasteryController(StudentRepository studentRepository, TopicMasteryRepository topicMasteryRepository) {
        this.studentRepository = studentRepository;
        this.topicMasteryRepository = topicMasteryRepository;
    }

    @GetMapping("/heatmap")
    public ResponseEntity<?> getMyMasteryHeatmap() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || "anonymousUser".equals(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Optional<Student> studentOpt = studentRepository.findByEmailIgnoreCase(email);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }

        Student student = studentOpt.get();
        List<Map<String, Object>> heatmapData = topicMasteryRepository.findByStudent(student)
                .stream()
                .map(mastery -> {
                    // Map decay rate to a 0-4 heat index
                    // 0 = Cool/Mastered (low decay)
                    // 4 = Hot/Danger (high decay)
                    int heatIndex = 0;
                    double decay = mastery.getCurrentDecayRate();
                    if (decay > 0.5) heatIndex = 4;
                    else if (decay > 0.3) heatIndex = 3;
                    else if (decay > 0.1) heatIndex = 2;
                    else if (decay > 0.0) heatIndex = 1;

                    return Map.<String, Object>of(
                            "topic", mastery.getTopicSlug(),
                            "decayRate", decay,
                            "heatIndex", heatIndex,
                            "easinessFactor", mastery.getEasinessFactor()
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(heatmapData);
    }
}
