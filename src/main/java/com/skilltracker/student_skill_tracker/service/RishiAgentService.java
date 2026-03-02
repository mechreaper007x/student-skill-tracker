package com.skilltracker.student_skill_tracker.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.dto.RishiAgentExecuteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiAgentExecuteResponse;
import com.skilltracker.student_skill_tracker.dto.RishiGrowthSummaryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleRequest;
import com.skilltracker.student_skill_tracker.model.RishiPracticeTask;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.model.TopicMastery;
import com.skilltracker.student_skill_tracker.repository.RishiPracticeTaskRepository;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.repository.TopicMasteryRepository;

@Service
public class RishiAgentService {

        private static final String DEFAULT_PROVIDER = "mistral";
        private static final String DEFAULT_MODEL = "mistral-small-latest";
        private static final int MAX_CONTEXT_MESSAGES = 6;

        private final RishiGenAiService rishiGenAiService;
        private final RishiAgentAiService rishiAgentAiService;
        private final RishiMemoryService rishiMemoryService;
        private final RishiIntegrationService rishiIntegrationService;
        private final RishiPracticeTaskRepository rishiPracticeTaskRepository;
        private final RishiCodingTelemetryService rishiCodingTelemetryService;
        private final SkillDataRepository skillDataRepository;
        private final TopicMasteryRepository topicMasteryRepository;
        private final TokenCryptoService tokenCryptoService;
        private final StudentRepository studentRepository;
        private final CodeDiffService codeDiffService;
        private final MistakePatternService mistakePatternService;

        public RishiAgentService(
                        RishiGenAiService rishiGenAiService,
                        RishiAgentAiService rishiAgentAiService,
                        RishiMemoryService rishiMemoryService,
                        RishiIntegrationService rishiIntegrationService,
                        RishiPracticeTaskRepository rishiPracticeTaskRepository,
                        RishiCodingTelemetryService rishiCodingTelemetryService,
                        SkillDataRepository skillDataRepository,
                        TopicMasteryRepository topicMasteryRepository,
                        TokenCryptoService tokenCryptoService,
                        StudentRepository studentRepository,
                        CodeDiffService codeDiffService,
                        MistakePatternService mistakePatternService) {
                this.rishiGenAiService = rishiGenAiService;
                this.rishiAgentAiService = rishiAgentAiService;
                this.rishiMemoryService = rishiMemoryService;
                this.rishiIntegrationService = rishiIntegrationService;
                this.rishiPracticeTaskRepository = rishiPracticeTaskRepository;
                this.rishiCodingTelemetryService = rishiCodingTelemetryService;
                this.skillDataRepository = skillDataRepository;
                this.topicMasteryRepository = topicMasteryRepository;
                this.tokenCryptoService = tokenCryptoService;
                this.studentRepository = studentRepository;
                this.codeDiffService = codeDiffService;
                this.mistakePatternService = mistakePatternService;
        }

        @Transactional
        public RishiAgentExecuteResponse execute(Student student, RishiAgentExecuteRequest request) {
                if (request == null || isBlank(request.getMessage())) {
                        throw new IllegalArgumentException("Message is required");
                }

                String encryptedApiKey = student.getAiApiKeyEncrypted();
                if (isBlank(encryptedApiKey)) {
                        throw new IllegalArgumentException(
                                        "GenAI API key is not configured. Attach your key in settings.");
                }

                String apiKey = tokenCryptoService.decrypt(encryptedApiKey);
                String model = isBlank(request.getModel())
                                ? (isBlank(student.getAiModel()) ? DEFAULT_MODEL : student.getAiModel())
                                : request.getModel().trim();

                List<RishiMemoryService.MemoryMessage> memory = List.of();
                String threadId = request.getThreadId();
                if (!isBlank(threadId)) {
                        RishiMemoryService.ChatThread thread = rishiMemoryService.getThread(student, threadId);
                        if (thread != null && thread.messages != null) {
                                memory = thread.messages;
                        }
                }
                List<Map<String, String>> context = rishiMemoryService.toModelContext(memory, MAX_CONTEXT_MESSAGES);

                String studentSnapshot = buildSkillSnapshot(student);
                String agentContext = buildAgentContext(student);
                String agentSystemPrompt = buildAgentSystemPrompt(studentSnapshot, agentContext);

                String reply;
                try {
                        // Primary: LangChain4j AiServices with tool calling
                        RishiAgentAiService.AgentResponse agentResponse = rishiAgentAiService.executeWithTools(
                                        apiKey, model, student, request.getMessage().trim(), agentSystemPrompt);
                        reply = agentResponse.reply();
                } catch (Exception toolCallEx) {
                        // Fallback: Original RishiGenAiService (no tool calling, but proven stable)
                        org.slf4j.LoggerFactory.getLogger(RishiAgentService.class)
                                        .warn("LangChain4j tool calling failed, falling back to raw GenAI: {}",
                                                        toolCallEx.getMessage());
                        reply = rishiGenAiService.generateLearningResponse(
                                        apiKey, model,
                                        "AGENT MODE REQUEST:\n" + request.getMessage().trim(),
                                        context,
                                        studentSnapshot + "\n\n" + agentContext,
                                        student.getLastEmotionAfterFailure());
                }

                List<String> actions = new ArrayList<>();
                actions.add("Agent context merged: coding telemetry + practice tasks + calendar availability.");
                var coachingSummary = rishiIntegrationService.getCoachingSummary(student, 7);
                actions.add("Daily coaching summary generated: focus=" + coachingSummary.getRecommendedFocus()
                                + ", minutes=" + coachingSummary.getRecommendedMinutesToday());

                boolean autoSchedule = Boolean.TRUE.equals(request.getAutoSchedule());
                if (autoSchedule && rishiIntegrationService.isGoogleCalendarConnected(student)) {
                        RishiScheduleRequest scheduleRequest = new RishiScheduleRequest();
                        scheduleRequest.setCount(2);
                        scheduleRequest.setBlockMinutes(50);
                        var scheduleResponse = rishiIntegrationService.autoRescheduleMissedBlocks(student,
                                        scheduleRequest);
                        actions.add("Auto-rescheduled/scheduled " + scheduleResponse.getScheduledCount()
                                        + " study block(s) in Google Calendar.");
                }

                String persistedThreadId = rishiMemoryService.appendExchange(
                                student,
                                threadId,
                                request.getMessage(),
                                "text",
                                reply,
                                "strategy");
                student.setRishiMode("agent");
                studentRepository.save(student);

                return RishiAgentExecuteResponse.builder()
                                .reply(reply)
                                .provider(DEFAULT_PROVIDER)
                                .model(model)
                                .mode("agent")
                                .threadId(persistedThreadId)
                                .actions(actions)
                                .build();
        }

        private String buildAgentContext(Student student) {
                List<RishiPracticeTask> tasks = rishiPracticeTaskRepository.findByStudentOrderByUpdatedAtDesc(student);
                long pendingTasks = tasks.stream().filter(task -> !"DONE".equalsIgnoreCase(task.getStatus())).count();

                StringBuilder ctx = new StringBuilder();

                // Growth summary (always relevant)
                RishiGrowthSummaryResponse growthSummary = rishiCodingTelemetryService.getGrowthSummary(student, 14);
                ctx.append("Growth(14d): codingMin=").append(growthSummary.getCurrent().getTotalCodingMinutes())
                                .append("/").append(growthSummary.getPrevious().getTotalCodingMinutes())
                                .append(", successGrowth=")
                                .append(String.format("%.1f%%", growthSummary.getSuccessRateGrowthPct()))
                                .append("\n");

                // Integrations status (compact)
                ctx.append("Integrations: calendar=").append(rishiIntegrationService.isGoogleCalendarConnected(student))
                                .append(", pendingTasks=").append(pendingTasks).append("\n");

                // Only include analytics that are actually connected (skip "not synced")
                rishiIntegrationService.getLatestGithubAnalytics(student)
                                .ifPresent(a -> ctx.append("GitHub(").append(a.getWindowDays()).append("d): commits=")
                                                .append(a.getCommitCount()).append(", prs=")
                                                .append(a.getPullRequestCount())
                                                .append("\n"));
                rishiIntegrationService.getLatestLeetCodeAnalytics(student)
                                .ifPresent(a -> ctx.append("LC(").append(a.getWindowDays()).append("d): solved=")
                                                .append(a.getTotalSolved()).append(" e/m/h=").append(a.getEasySolved())
                                                .append("/").append(a.getMediumSolved()).append("/")
                                                .append(a.getHardSolved())
                                                .append("\n"));
                rishiIntegrationService.getLatestCodeforcesAnalytics(student)
                                .ifPresent(a -> ctx.append("CF: rating=").append(a.getCurrentRating())
                                                .append(", max=").append(a.getMaxRating()).append("\n"));

                // Cognitive metrics (always relevant, compact)
                ctx.append("Cog: recovMs=").append(student.getAvgRecoveryVelocityMs())
                                .append(", planMs=").append(student.getAvgPlanningTimeMs())
                                .append(", style=")
                                .append(student.getThinkingStyle() != null ? student.getThinkingStyle() : "?")
                                .append(", locked=").append(student.isCompilerLocked()).append("\n");

                // Top 3 pending tasks (reduced from 5)
                String topTasks = tasks.stream()
                                .filter(task -> !"DONE".equalsIgnoreCase(task.getStatus()))
                                .limit(3)
                                .map(task -> "- " + task.getTitle() + " [" + task.getStatus() + "]")
                                .reduce("", (a, b) -> a + b + "\n");
                if (!topTasks.isBlank()) {
                        ctx.append("Tasks:\n").append(topTasks);
                }

                // Code diff analysis (last 5 compile attempts)
                String diffSummary = codeDiffService.generateDiffSummary(student);
                if (!diffSummary.isBlank()) {
                        ctx.append(diffSummary);
                }

                // Mistake pattern weaknesses (last 14 days)
                String weaknessSummary = mistakePatternService.getWeaknessSummary(student, 14);
                if (!weaknessSummary.isBlank()) {
                        ctx.append(weaknessSummary).append("\n");
                }

                return ctx.toString();
        }

        private String buildSkillSnapshot(Student student) {
                SkillData latest = skillDataRepository.findTopByStudentOrderByCreatedAtDesc(student).orElse(null);
                String snapshot = "No tracked scores yet.";

                if (latest != null) {
                        int totalSolved = latest.getTotalProblemsSolved() == null ? 0 : latest.getTotalProblemsSolved();
                        snapshot = String.format(
                                        "ProblemSolving=%.1f, Algorithms=%.1f, DataStructures=%.1f, TotalSolved=%d, Easy=%d, Medium=%d, Hard=%d",
                                        safe(latest.getProblemSolvingScore()),
                                        safe(latest.getAlgorithmsScore()),
                                        safe(latest.getDataStructuresScore()),
                                        totalSolved,
                                        latest.getEasyProblems() == null ? 0 : latest.getEasyProblems(),
                                        latest.getMediumProblems() == null ? 0 : latest.getMediumProblems(),
                                        latest.getHardProblems() == null ? 0 : latest.getHardProblems());
                }

                List<TopicMastery> masteries = topicMasteryRepository.findByStudent(student);
                if (masteries != null && !masteries.isEmpty()) {
                        String decayingTopics = masteries.stream()
                                        .filter(m -> m.getCurrentDecayRate() > 0.3)
                                        .map(m -> m.getTopicSlug() + " (Decay: "
                                                        + String.format("%.2f", m.getCurrentDecayRate()) + ")")
                                        .reduce((a, b) -> a + ", " + b)
                                        .orElse("");
                        if (!decayingTopics.isBlank()) {
                                snapshot += "\n[CRITICAL SM-2 DECAY]: " + decayingTopics;
                        }
                }

                return snapshot;
        }

        private String buildAgentSystemPrompt(String skillSnapshot, String agentContext) {
                return """
                                You are Rishi, AGENT MODE. 10x Senior Dev mentor with tool authority.

                                STUDENT:
                                %s

                                CONTEXT:
                                %s

                                RULES:
                                - Call getTelemetrySnapshot() first before interventions
                                - lockCompiler: rage-compiling or forced break (max 30min)
                                - triggerAmbushDuel: stagnation or poor recovery (>120s)
                                - roastLogic: bad habits (low planning time, ignoring decay)
                                - scheduleStudyBlock: SM-2 decay detected
                                - NEVER give direct code solutions. Use Socratic questions.
                                - Format: Observation → Reasoning → Action → Guidance
                                - Be concise and action-first.
                                """
                                .formatted(skillSnapshot, agentContext);
        }

        private double safe(Double value) {
                return value == null ? 0.0 : value;
        }

        private boolean isBlank(String value) {
                return value == null || value.isBlank();
        }
}
