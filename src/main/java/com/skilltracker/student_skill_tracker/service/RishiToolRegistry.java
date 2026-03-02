package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.model.RishiPracticeTask;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiPracticeTaskRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Rishi's arsenal — the bridge between the LLM's reasoning and the physical
 * system.
 * Each @Tool method is a named action that the AI can invoke via function
 * calling.
 *
 * Uses ThreadLocal for thread-safe per-request student binding.
 */
@Component
public class RishiToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RishiToolRegistry.class);

    private final StudentRepository studentRepository;
    private final DuelService duelService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RishiPracticeTaskRepository practiceTaskRepository;
    private final SmartQuestionRouter smartQuestionRouter;

    private final ThreadLocal<Student> targetStudentHolder = new ThreadLocal<>();

    public RishiToolRegistry(
            StudentRepository studentRepository,
            DuelService duelService,
            SimpMessagingTemplate messagingTemplate,
            RishiPracticeTaskRepository practiceTaskRepository,
            SmartQuestionRouter smartQuestionRouter) {
        this.studentRepository = studentRepository;
        this.duelService = duelService;
        this.messagingTemplate = messagingTemplate;
        this.practiceTaskRepository = practiceTaskRepository;
        this.smartQuestionRouter = smartQuestionRouter;
    }

    public void bindStudent(Student student) {
        this.targetStudentHolder.set(student);
    }

    public void clearStudent() {
        this.targetStudentHolder.remove();
    }

    private Student target() {
        return targetStudentHolder.get();
    }

    // ===== TOOL: LOCK COMPILER =====

    @Tool("Locks the student's compiler for a specified number of minutes. Use when rage-compiling or forced break needed.")
    @Transactional
    public String lockCompiler(
            @P("Number of minutes to lock the compiler (1-30)") int durationMinutes,
            @P("Human-readable reason for the lock") String reason) {

        Student s = target();
        if (s == null)
            return "ERROR: No student bound.";
        int safeDuration = Math.max(1, Math.min(durationMinutes, 30));

        s.setCompilerLockedUntil(LocalDateTime.now().plusMinutes(safeDuration));
        s.setCompilerLockReason(reason);
        s.setAgentLastInterventionAt(LocalDateTime.now());
        studentRepository.save(s);

        messagingTemplate.convertAndSend(
                "/topic/agent/" + s.getId(),
                java.util.Map.of(
                        "type", "COMPILER_LOCKED",
                        "durationMinutes", safeDuration,
                        "reason", reason,
                        "until", s.getCompilerLockedUntil().toString()));

        logger.info("AGENT: Locked compiler for {} for {}min: {}", s.getEmail(), safeDuration, reason);
        return "Compiler locked for " + safeDuration + " minutes. Reason: " + reason;
    }

    // ===== TOOL: TRIGGER AMBUSH DUEL =====

    @Tool("Forces the student into an immediate DojoPuzzle duel. Use when idle, recovery velocity poor, or stagnation.")
    @Transactional
    public String triggerAmbushDuel(
            @P("Reason for triggering the duel") String reason) {

        Student s = target();
        if (s == null)
            return "ERROR: No student bound.";

        s.setAgentLastInterventionAt(LocalDateTime.now());
        studentRepository.save(s);

        messagingTemplate.convertAndSend(
                "/topic/agent/" + s.getId(),
                java.util.Map.of(
                        "type", "AMBUSH_DUEL",
                        "reason", reason,
                        "timestamp", LocalDateTime.now().toString()));

        logger.info("AGENT: Ambush duel for {}: {}", s.getEmail(), reason);
        return "Ambush duel triggered. Reason: " + reason;
    }

    // ===== TOOL: ROAST LOGIC =====

    @Tool("Delivers a stern constructive critique of the student's coding behavior based on telemetry.")
    public String roastLogic(
            @P("The specific criticism or observation") String critique) {

        Student s = target();
        if (s == null)
            return "ERROR: No student bound.";

        messagingTemplate.convertAndSend(
                "/topic/agent/" + s.getId(),
                java.util.Map.of(
                        "type", "AGENT_ROAST",
                        "message", critique,
                        "timestamp", LocalDateTime.now().toString()));

        logger.info("AGENT: Roasted {}: {}", s.getEmail(), critique);
        return "Roast delivered: " + critique;
    }

    // ===== TOOL: GET TELEMETRY SNAPSHOT =====

    @Tool("Retrieves the student's current cognitive telemetry metrics for decision-making.")
    public String getTelemetrySnapshot() {
        Student s = target();
        if (s == null)
            return "ERROR: No student bound.";

        int totalComp = s.getTotalCompilations() == null ? 0 : s.getTotalCompilations();
        int successComp = s.getSuccessfulCompilations() == null ? 0 : s.getSuccessfulCompilations();
        double successRate = totalComp > 0 ? (double) successComp / totalComp * 100 : 0;

        return String.format(
                "TELEMETRY: compilations=%d/%d (%.1f%%), submissions=%d/%d, recoveryMs=%d, planningMs=%d, style=%s, emotion=%s, duels=%dW/%dL, level=%d, locked=%s",
                successComp, totalComp, successRate,
                s.getTotalSubmissions(), s.getAcceptedSubmissions(),
                s.getAvgRecoveryVelocityMs(),
                s.getAvgPlanningTimeMs(),
                s.getThinkingStyle() != null ? s.getThinkingStyle() : "unknown",
                s.getLastEmotionAfterFailure() != null ? s.getLastEmotionAfterFailure() : "none",
                s.getDuelWins(), s.getDuelLosses(),
                s.getLevel(),
                s.isCompilerLocked() ? "YES" : "NO");
    }

    // ===== TOOL: SCHEDULE STUDY BLOCK =====

    @Tool("Sends a study block recommendation for a specific topic. Use when SM-2 decay or neglected skills detected.")
    @Transactional
    public String scheduleStudyBlock(
            @P("The topic or skill area to study") String topic,
            @P("Recommended duration in minutes") int durationMinutes,
            @P("Reason for the recommendation") String reason) {

        Student s = target();
        if (s == null)
            return "ERROR: No student bound.";

        int clampedDuration = Math.max(15, Math.min(durationMinutes, 120));

        // Persist as a real practice task
        RishiPracticeTask task = RishiPracticeTask.builder()
                .student(s)
                .sourceType("AGENT_SM2")
                .sourceExternalId("sm2-" + topic + "-" + System.currentTimeMillis())
                .title("Study: " + topic)
                .details(reason)
                .topic(topic)
                .priority(2)
                .status("TODO")
                .suggestedMinutes(clampedDuration)
                .plannedStartAt(LocalDateTime.now().plusHours(1))
                .plannedEndAt(LocalDateTime.now().plusHours(1).plusMinutes(clampedDuration))
                .build();
        practiceTaskRepository.save(task);

        messagingTemplate.convertAndSend(
                "/topic/agent/" + s.getId(),
                java.util.Map.of(
                        "type", "STUDY_BLOCK_RECOMMENDATION",
                        "topic", topic,
                        "durationMinutes", clampedDuration,
                        "reason", reason,
                        "taskId", task.getId(),
                        "timestamp", LocalDateTime.now().toString()));

        logger.info("AGENT: Study block + task #{} for {}: {} ({}min): {}",
                task.getId(), s.getEmail(), topic, clampedDuration, reason);
        return "Study block created (Task #" + task.getId() + "): " + topic + " for " + clampedDuration
                + "min. Reason: " + reason;
    }

    // ===== TOOL: CREATE PRACTICE TASK =====

    @Tool("Creates a persistent practice task for the student. Use for escalation, curriculum generation, or SM-2 decay intervention.")
    @Transactional
    public String createPracticeTask(
            @P("The topic or problem slug") String topic,
            @P("Title of the practice task") String title,
            @P("Suggested duration in minutes") int suggestedMinutes,
            @P("Source type identifier (e.g. AUTO_ESCALATION_L2, AGENT_CURRICULUM)") String sourceType) {

        Student s = target();
        if (s == null)
            return "ERROR: No student bound.";

        int clampedMinutes = Math.max(10, Math.min(suggestedMinutes, 180));

        RishiPracticeTask task = RishiPracticeTask.builder()
                .student(s)
                .sourceType(sourceType != null ? sourceType : "AGENT")
                .sourceExternalId(sourceType + "-" + topic + "-" + System.currentTimeMillis())
                .title(title)
                .details("Auto-generated by Rishi agent for topic: " + topic)
                .topic(topic)
                .priority(3) // High priority for agent-generated tasks
                .status("TODO")
                .suggestedMinutes(clampedMinutes)
                .build();
        practiceTaskRepository.save(task);

        messagingTemplate.convertAndSend(
                "/topic/agent/" + s.getId(),
                java.util.Map.of(
                        "type", "PRACTICE_TASK_CREATED",
                        "taskId", task.getId(),
                        "title", title,
                        "topic", topic,
                        "timestamp", LocalDateTime.now().toString()));

        logger.info("AGENT: Practice task #{} created for {}: {} [{}]",
                task.getId(), s.getEmail(), title, sourceType);
        return "Practice task created (Task #" + task.getId() + "): " + title;
    }

    // ===== TOOL: RECOMMEND NEXT QUESTION =====

    @Tool("Recommend the optimal next practice question for the student based on their learning decay (SM-2) and recent mistake patterns.")
    public String recommendNextQuestion() {
        Student student = target();
        if (student == null) {
            return "No student context available for question recommendation.";
        }

        try {
            var recommendation = smartQuestionRouter.recommendNext(student);
            if (recommendation == null) {
                return "I couldn't find a specific recommendation. Let's try the Daily Challenge.";
            }

            String title = String.valueOf(recommendation.get("title"));
            String difficulty = String.valueOf(recommendation.get("difficulty"));
            String reason = String.valueOf(recommendation.get("_routingReason"));

            return String.format("I recommend: '%s' (Difficulty: %s).\nReason: %s",
                    title, difficulty, reason != null ? reason : "Targeted practice.");
        } catch (Exception e) {
            return "Error finding recommendation: " + e.getMessage();
        }
    }
}
