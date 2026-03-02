package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.RishiCompileAttemptLog;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiCompileAttemptLogRepository;

/**
 * Aggregates mistake categories from compile logs to surface weak spots.
 */
@Service
public class MistakePatternService {

    private final RishiCompileAttemptLogRepository attemptLogRepository;

    public MistakePatternService(RishiCompileAttemptLogRepository attemptLogRepository) {
        this.attemptLogRepository = attemptLogRepository;
    }

    /**
     * Returns top 3 mistake categories with counts from the last N days.
     * Example: {"NULL_REFERENCE": 12, "OFF_BY_ONE": 8, "INFINITE_LOOP": 5}
     */
    public Map<String, Long> getTopWeaknesses(Student student, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<RishiCompileAttemptLog> failedAttempts = attemptLogRepository
                .findBySessionStudentAndAttemptedAtAfterAndSuccessFalse(student, since);

        if (failedAttempts.isEmpty())
            return Collections.emptyMap();

        return failedAttempts.stream()
                .filter(a -> a.getMistakeCategory() != null && !a.getMistakeCategory().isBlank())
                .collect(Collectors.groupingBy(
                        RishiCompileAttemptLog::getMistakeCategory,
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * Compact string for agent context injection.
     */
    public String getWeaknessSummary(Student student, int days) {
        Map<String, Long> top = getTopWeaknesses(student, days);
        if (top.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder("Weaknesses(").append(days).append("d): ");
        top.forEach((category, count) -> sb.append(category).append("=").append(count).append(", "));
        // Remove trailing comma
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    /**
     * Returns the single most common mistake category.
     */
    public String getTopWeakness(Student student, int days) {
        Map<String, Long> top = getTopWeaknesses(student, days);
        return top.isEmpty() ? null : top.keySet().iterator().next();
    }
}
