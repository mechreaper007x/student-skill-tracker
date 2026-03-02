package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.model.TopicMastery;
import com.skilltracker.student_skill_tracker.repository.TopicMasteryRepository;

/**
 * Picks the optimal next question based on SM-2 decay + mistake patterns.
 * Transforms the Proving Grounds from random practice into a personalized
 * curriculum.
 */
@Service
public class SmartQuestionRouter {

    private static final Logger logger = LoggerFactory.getLogger(SmartQuestionRouter.class);

    private final TopicMasteryRepository topicMasteryRepository;
    private final MistakePatternService mistakePatternService;
    private final QuestionService questionService;

    public SmartQuestionRouter(
            TopicMasteryRepository topicMasteryRepository,
            MistakePatternService mistakePatternService,
            QuestionService questionService) {
        this.topicMasteryRepository = topicMasteryRepository;
        this.mistakePatternService = mistakePatternService;
        this.questionService = questionService;
    }

    /**
     * Returns a recommended question based on:
     * 1. Overdue SM-2 topics (highest priority)
     * 2. Top weakness from mistake patterns
     * 3. Difficulty based on easiness factor
     */
    public Map<String, Object> recommendNext(Student student) {
        // Step 1: Find the most overdue SM-2 topic
        List<TopicMastery> masteries = topicMasteryRepository.findByStudent(student);
        Optional<TopicMastery> overdueTopicOpt = masteries.stream()
                .filter(m -> m.getNextReviewDate() != null
                        && m.getNextReviewDate().isBefore(LocalDateTime.now()))
                .min(Comparator.comparing(TopicMastery::getNextReviewDate)); // Most overdue first

        String targetTag;
        String difficultyHint;
        String reason;

        if (overdueTopicOpt.isPresent()) {
            TopicMastery overdue = overdueTopicOpt.get();
            targetTag = overdue.getTopicSlug();
            difficultyHint = overdue.getEasinessFactor() > 2.0 ? "Medium" : "Easy";
            long daysSince = java.time.Duration.between(
                    overdue.getLastReviewedAt() != null ? overdue.getLastReviewedAt()
                            : overdue.getNextReviewDate(),
                    LocalDateTime.now()).toDays();
            reason = "SM-2 decay: '" + humanize(targetTag) + "' overdue by " + daysSince + " days (EF=" +
                    String.format("%.1f", overdue.getEasinessFactor()) + ")";
        } else {
            // Step 2: Fall back to top weakness from mistake patterns
            String topWeakness = mistakePatternService.getTopWeakness(student, 14);
            if (topWeakness != null) {
                targetTag = weaknessToTag(topWeakness);
                difficultyHint = "Easy"; // Rebuild confidence on weak areas
                reason = "Weakness: '" + topWeakness + "' — frequent error pattern detected";
            } else {
                // Step 3: No decay, no weakness — serve a medium challenge
                targetTag = null;
                difficultyHint = "Medium";
                reason = "No specific weakness detected. General practice.";
            }
        }

        // Find a matching question
        Map<String, Object> question = findQuestion(targetTag, difficultyHint);

        if (question == null) {
            // Fallback: daily challenge
            question = questionService.getDailyChallenge();
            reason = "No matching questions for '" + targetTag + "'. Serving daily challenge instead.";
        }

        // Enrich with routing metadata
        if (question != null) {
            question.put("_routingReason", reason);
            question.put("_targetTag", targetTag != null ? targetTag : "general");
            question.put("_suggestedDifficulty", difficultyHint);
        }

        logger.info("Smart pick for {}: tag={}, difficulty={}, reason={}",
                student.getEmail(), targetTag, difficultyHint, reason);
        return question;
    }

    private Map<String, Object> findQuestion(String tag, String difficulty) {
        if (tag != null) {
            // Try tag + difficulty match first
            List<Map<String, Object>> byTag = questionService.getByTag(tag);
            Optional<Map<String, Object>> match = byTag.stream()
                    .filter(q -> difficulty.equalsIgnoreCase(String.valueOf(q.get("difficulty"))))
                    .findFirst();
            if (match.isPresent())
                return match.get();

            // Tag match without difficulty filter
            if (!byTag.isEmpty())
                return byTag.get(0);
        }

        // Pure difficulty match
        List<Map<String, Object>> byDiff = questionService.getByDifficulty(difficulty);
        if (!byDiff.isEmpty()) {
            // Random pick to avoid always serving the same question
            int idx = (int) (System.currentTimeMillis() % byDiff.size());
            return byDiff.get(idx);
        }

        return null;
    }

    /**
     * Maps mistake categories to question bank tags.
     */
    private String weaknessToTag(String mistakeCategory) {
        if (mistakeCategory == null)
            return "array";
        return switch (mistakeCategory.toUpperCase()) {
            case "NULL_REFERENCE", "NULL_POINTER" -> "linked-list";
            case "OFF_BY_ONE", "INDEX_OUT_OF_BOUNDS" -> "array";
            case "INFINITE_LOOP", "TIMEOUT" -> "dynamic-programming";
            case "STACK_OVERFLOW", "RECURSION_ERROR" -> "recursion";
            case "TYPE_MISMATCH", "CAST_ERROR" -> "string";
            case "LOGIC_ERROR" -> "math";
            case "CONCURRENCY", "RACE_CONDITION" -> "concurrency";
            default -> "array"; // Safe default
        };
    }

    private String humanize(String slug) {
        if (slug == null)
            return "unknown";
        return slug.replace("-", " ").replace("_", " ");
    }
}
