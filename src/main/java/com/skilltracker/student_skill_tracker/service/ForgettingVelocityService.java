package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.model.MasteryEventLog;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.model.TopicMastery;
import com.skilltracker.student_skill_tracker.repository.MasteryEventLogRepository;
import com.skilltracker.student_skill_tracker.repository.TopicMasteryRepository;

@Service
public class ForgettingVelocityService {

    private static final Logger log = LoggerFactory.getLogger(ForgettingVelocityService.class);

    private final TopicMasteryRepository masteryRepo;
    private final MasteryEventLogRepository eventLogRepo;

    public ForgettingVelocityService(TopicMasteryRepository masteryRepo, MasteryEventLogRepository eventLogRepo) {
        this.masteryRepo = masteryRepo;
        this.eventLogRepo = eventLogRepo;
    }

    /**
     * Records a cognitive event (e.g., a compilation run or a duel round) and updates
     * the SM-2 learning curve based on behavioral telemetry.
     */
    @Transactional
    public void recordEventAndUpdateMastery(Student student, String topicSlug, String eventType, 
                                            long timeTakenMs, int errorCount, boolean success, 
                                            boolean highPressure, String metadata) {
        if (student == null || topicSlug == null || topicSlug.isBlank()) {
            return;
        }

        // 1. Calculate Quality Score (0-5) based on behavioral telemetry
        int qualityScore = calculateQualityScore(timeTakenMs, errorCount, success, highPressure);

        // 2. Log the event
        MasteryEventLog eventLog = MasteryEventLog.builder()
                .student(student)
                .topicSlug(topicSlug)
                .eventType(eventType)
                .timeTakenMs(timeTakenMs)
                .errorCount(errorCount)
                .success(success)
                .qualityScore(qualityScore)
                .highPressure(highPressure)
                .metadataJson(metadata)
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepo.save(eventLog);

        // 3. Retrieve or initialize Mastery
        TopicMastery mastery = masteryRepo.findByStudentAndTopicSlug(student, topicSlug)
                .orElseGet(() -> TopicMastery.builder()
                        .student(student)
                        .topicSlug(topicSlug)
                        .repetitions(0)
                        .easinessFactor(2.5)
                        .intervalDays(0)
                        .currentDecayRate(0.0)
                        .bestTimeMs(timeTakenMs)
                        .totalStruggleEvents(0)
                        .build());

        // 4. Calculate Forgetting Velocity / Decay
        updateDecayRate(mastery, timeTakenMs, errorCount, success);

        // 5. Apply SM-2 Algorithm
        applySm2Logic(mastery, qualityScore, highPressure);

        mastery.setLastReviewedAt(LocalDateTime.now());
        masteryRepo.save(mastery);
        
        log.info("Updated mastery for {} on topic {}: EF={}, Interval={}, Decay={}", 
            student.getEmail(), topicSlug, mastery.getEasinessFactor(), mastery.getIntervalDays(), mastery.getCurrentDecayRate());
    }

    /**
     * SM-2 logic combined with our custom decay metrics.
     */
    private void applySm2Logic(TopicMastery mastery, int quality, boolean highPressure) {
        int n = mastery.getRepetitions();
        double ef = mastery.getEasinessFactor();
        int interval = mastery.getIntervalDays();

        if (quality >= 3) {
            // Correct response
            if (n == 0) {
                interval = 1;
            } else if (n == 1) {
                interval = 6;
            } else {
                interval = (int) Math.round(interval * ef);
            }
            mastery.setRepetitions(n + 1);
        } else {
            // Incorrect or major struggle
            mastery.setRepetitions(0);
            interval = 1;
            mastery.setTotalStruggleEvents(mastery.getTotalStruggleEvents() + 1);
        }

        // Standard SM-2 formula for EF
        ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        
        // Pressure modifier: If they fail under high pressure, EF drops slightly faster 
        // to ensure the topic is reviewed sooner.
        if (highPressure && quality < 3) {
            ef -= 0.1;
        }

        ef = Math.max(1.3, ef); // Minimum EF is 1.3

        mastery.setEasinessFactor(ef);
        mastery.setIntervalDays(interval);
        mastery.setNextReviewDate(LocalDateTime.now().plusDays(interval));
    }

    /**
     * Determines the quality of the recall (q: 0-5)
     * 5: Perfect response
     * 4: Correct response after a hesitation
     * 3: Correct response recalled with serious difficulty
     * 2: Incorrect response; where the correct one seemed easy to recall
     * 1: Incorrect response; the correct one remembered
     * 0: Complete blackout.
     */
    private int calculateQualityScore(long timeTakenMs, int errorCount, boolean success, boolean highPressure) {
        if (!success) {
            if (errorCount > 3) return 0; // Complete blackout / thrashing
            if (errorCount > 0) return 1; // Incorrect, struggling
            return 2; // Incorrect, but few syntax errors (logical flaw)
        }

        if (errorCount == 0 && timeTakenMs < 60000) {
            return 5; // Fast and flawless
        }
        
        if (errorCount <= 1 || (highPressure && timeTakenMs < 120000)) {
            return 4; // Minor hesitation or fast under pressure
        }
        
        return 3; // Solved, but took multiple tries/errors
    }

    /**
     * Calculates the "Velocity" at which the student is forgetting this topic.
     */
    private void updateDecayRate(TopicMastery mastery, long timeTakenMs, int errorCount, boolean success) {
        if (!success) {
            mastery.setCurrentDecayRate(mastery.getCurrentDecayRate() + 0.1); // Decay increases on failure
            return;
        }

        if (mastery.getBestTimeMs() == 0 || timeTakenMs < mastery.getBestTimeMs()) {
            mastery.setBestTimeMs(timeTakenMs);
            mastery.setCurrentDecayRate(Math.max(0.0, mastery.getCurrentDecayRate() - 0.05)); // Decay decreases on new best
        } else {
            // How much slower were they this time compared to their best?
            double timeRatio = (double) timeTakenMs / mastery.getBestTimeMs();
            if (timeRatio > 1.5) {
                // Took 50% longer than their best time. They are forgetting.
                mastery.setCurrentDecayRate(mastery.getCurrentDecayRate() + (timeRatio * 0.02));
            }
        }
    }
}
