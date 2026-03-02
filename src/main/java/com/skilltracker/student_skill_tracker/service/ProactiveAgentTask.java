package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

/**
 * The Pulse — Rishi's autonomous nervous system.
 *
 * This @Scheduled background task runs periodically, scanning all active
 * students'
 * telemetry for intervention-worthy patterns. When thresholds are breached, it
 * pushes real-time notifications via WebSocket, giving Rishi proactive
 * authority
 * without waiting for the student to ask for help.
 *
 * Current intervention triggers:
 * 1. RAGE-COMPILE DETECTION: Success rate below 30% with >10 compilations
 * 2. STAGNATION ALERT: Recovery velocity > 2 minutes (120,000ms)
 * 3. DECAY WARNING: Students with topics in critical SM-2 decay (handled
 * elsewhere but re-notified)
 */
@Component
public class ProactiveAgentTask {

    private static final Logger logger = LoggerFactory.getLogger(ProactiveAgentTask.class);

    private static final double RAGE_COMPILE_THRESHOLD = 0.20;
    private static final int MIN_COMPILATIONS_FOR_ANALYSIS = 5;
    private static final long STAGNATION_VELOCITY_MS = 120_000;
    private static final long INTERVENTION_COOLDOWN_MINUTES = 60;

    private final StudentRepository studentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RishiToolRegistry rishiToolRegistry;

    public ProactiveAgentTask(
            StudentRepository studentRepository,
            SimpMessagingTemplate messagingTemplate,
            RishiToolRegistry rishiToolRegistry) {
        this.studentRepository = studentRepository;
        this.messagingTemplate = messagingTemplate;
        this.rishiToolRegistry = rishiToolRegistry;
    }

    /**
     * Runs every 5 minutes. Scans all students for intervention-worthy telemetry
     * patterns.
     */
    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void scanTelemetryAndIntervene() {
        logger.debug("Pulse: Scanning student telemetry...");

        List<Student> students = studentRepository.findAll();
        int interventionCount = 0;

        for (Student student : students) {
            if (isOnCooldown(student))
                continue;

            if (detectRageCompiling(student)) {
                // Hard lock instead of just a warning
                rishiToolRegistry.bindStudent(student);
                try {
                    rishiToolRegistry.lockCompiler(10,
                            "Background scan: compilation success rate critically low. Forced reflection.");
                } finally {
                    rishiToolRegistry.clearStudent();
                }
                interventionCount++;
            }

            if (detectStagnation(student)) {
                pushIntervention(student, "STAGNATION_ALERT",
                        "Your recovery velocity is dangerously slow. You're spending too long stuck after failures. Try breaking the problem into smaller pieces.");
                interventionCount++;
            }
        }

        if (interventionCount > 0) {
            logger.info("Pulse: Triggered {} intervention(s) across {} students",
                    interventionCount, students.size());
        }
    }

    private boolean detectRageCompiling(Student student) {
        int total = student.getTotalCompilations() == null ? 0 : student.getTotalCompilations();
        int success = student.getSuccessfulCompilations() == null ? 0 : student.getSuccessfulCompilations();

        if (total < MIN_COMPILATIONS_FOR_ANALYSIS)
            return false;

        double successRate = (double) success / total;
        return successRate < RAGE_COMPILE_THRESHOLD;
    }

    private boolean detectStagnation(Student student) {
        Long velocity = student.getAvgRecoveryVelocityMs();
        return velocity != null && velocity > STAGNATION_VELOCITY_MS;
    }

    private boolean isOnCooldown(Student student) {
        LocalDateTime lastIntervention = student.getAgentLastInterventionAt();
        if (lastIntervention == null)
            return false;
        return lastIntervention.plusMinutes(INTERVENTION_COOLDOWN_MINUTES).isAfter(LocalDateTime.now());
    }

    private void pushIntervention(Student student, String type, String message) {
        student.setAgentLastInterventionAt(LocalDateTime.now());
        studentRepository.save(student);

        messagingTemplate.convertAndSend(
                "/topic/agent/" + student.getId(),
                java.util.Map.of(
                        "type", type,
                        "message", message,
                        "timestamp", LocalDateTime.now().toString()));

        logger.info("Pulse intervention [{}] for student {}: {}", type, student.getEmail(), message);
    }
}
