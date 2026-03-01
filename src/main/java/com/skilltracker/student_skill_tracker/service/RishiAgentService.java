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
    private static final String DEFAULT_MODEL = "open-mixtral-8x7b";
    private static final int MAX_CONTEXT_MESSAGES = 12;

    private final RishiGenAiService rishiGenAiService;
    private final RishiMemoryService rishiMemoryService;
    private final RishiIntegrationService rishiIntegrationService;
    private final RishiPracticeTaskRepository rishiPracticeTaskRepository;
    private final RishiCodingTelemetryService rishiCodingTelemetryService;
    private final SkillDataRepository skillDataRepository;
    private final TopicMasteryRepository topicMasteryRepository;
    private final TokenCryptoService tokenCryptoService;
    private final StudentRepository studentRepository;

    public RishiAgentService(
            RishiGenAiService rishiGenAiService,
            RishiMemoryService rishiMemoryService,
            RishiIntegrationService rishiIntegrationService,
            RishiPracticeTaskRepository rishiPracticeTaskRepository,
            RishiCodingTelemetryService rishiCodingTelemetryService,
            SkillDataRepository skillDataRepository,
            TopicMasteryRepository topicMasteryRepository,
            TokenCryptoService tokenCryptoService,
            StudentRepository studentRepository) {
        this.rishiGenAiService = rishiGenAiService;
        this.rishiMemoryService = rishiMemoryService;
        this.rishiIntegrationService = rishiIntegrationService;
        this.rishiPracticeTaskRepository = rishiPracticeTaskRepository;
        this.rishiCodingTelemetryService = rishiCodingTelemetryService;
        this.skillDataRepository = skillDataRepository;
        this.topicMasteryRepository = topicMasteryRepository;
        this.tokenCryptoService = tokenCryptoService;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public RishiAgentExecuteResponse execute(Student student, RishiAgentExecuteRequest request) {
        if (request == null || isBlank(request.getMessage())) {
            throw new IllegalArgumentException("Message is required");
        }

        String encryptedApiKey = student.getAiApiKeyEncrypted();
        if (isBlank(encryptedApiKey)) {
            throw new IllegalArgumentException("GenAI API key is not configured. Attach your key in settings.");
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
        String prompt = """
                AGENT MODE REQUEST:
                %s

                Constraints:
                1) Be concise and action-first.
                2) If calendar integration is connected, propose concrete calendar blocks.
                3) If practice tasks exist, prioritize weak/urgent items first.
                4) Recommend today's next best action in <= 5 bullets.
                """.formatted(request.getMessage().trim());

        String reply = rishiGenAiService.generateLearningResponse(
                apiKey,
                model,
                prompt,
                context,
                studentSnapshot + "\n\n" + agentContext);

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
            var scheduleResponse = rishiIntegrationService.autoRescheduleMissedBlocks(student, scheduleRequest);
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

        RishiGrowthSummaryResponse growthSummary = rishiCodingTelemetryService.getGrowthSummary(student, 14);
        String growthLine = "Coding minutes (14d current/prev): "
                + growthSummary.getCurrent().getTotalCodingMinutes()
                + "/"
                + growthSummary.getPrevious().getTotalCodingMinutes()
                + ", success rate growth: "
                + String.format("%.1f%%", growthSummary.getSuccessRateGrowthPct());

        String integrationLine = "GoogleCalendarConnected=" + rishiIntegrationService.isGoogleCalendarConnected(student)
                + ", PendingTasks=" + pendingTasks;
        String githubLine = rishiIntegrationService.getLatestGithubAnalytics(student)
                .map(analytics -> "GitHub(" + analytics.getWindowDays() + "d): commits=" + analytics.getCommitCount()
                        + ", prs=" + analytics.getPullRequestCount()
                        + ", reviews=" + analytics.getReviewCount()
                        + ", activeRepos=" + analytics.getActiveRepoCount())
                .orElse("GitHub analytics: not synced");
        String leetCodeLine = rishiIntegrationService.getLatestLeetCodeAnalytics(student)
                .map(analytics -> "LeetCode(" + analytics.getWindowDays() + "d): solved=" + analytics.getTotalSolved()
                        + ", easy/med/hard=" + analytics.getEasySolved() + "/" + analytics.getMediumSolved() + "/"
                        + analytics.getHardSolved()
                        + ", trend7d=" + String.format("%.1f%%", analytics.getSolveTrendPct()))
                .orElse("LeetCode analytics: not synced");
        String codeforcesLine = rishiIntegrationService.getLatestCodeforcesAnalytics(student)
                .map(analytics -> "Codeforces(" + analytics.getWindowDays() + "d): rating=" + analytics.getCurrentRating()
                        + ", maxRating=" + analytics.getMaxRating()
                        + ", solvedWindow=" + analytics.getSolvedCurrentWindow()
                        + ", trend=" + String.format("%.1f%%", analytics.getSolveTrendPct()))
                .orElse("Codeforces analytics: not synced");
        var focusMetrics = rishiIntegrationService.getFocusMetrics(student, 7);
        var coachingSummary = rishiIntegrationService.getCoachingSummary(student, 7);
        String focusLine = "Focus(7d): plannedMinutes=" + focusMetrics.getPlannedMinutes()
                + ", actualMinutes=" + focusMetrics.getActualMinutes()
                + ", adherence=" + String.format("%.1f%%", focusMetrics.getAdherencePct());
        String coachingLine = "Coaching: focus=" + coachingSummary.getRecommendedFocus()
                + ", minutesToday=" + coachingSummary.getRecommendedMinutesToday()
                + ", missedTasks=" + coachingSummary.getMissedTasks();

        String topTasks = tasks.stream()
                .filter(task -> !"DONE".equalsIgnoreCase(task.getStatus()))
                .limit(5)
                .map(task -> "- " + task.getTitle() + " [" + task.getStatus() + "]")
                .reduce("", (a, b) -> a + b + "\n");

        return integrationLine + "\n" + growthLine + "\n" + githubLine + "\n" + leetCodeLine + "\n" + codeforcesLine + "\n"
                + focusLine + "\n" + coachingLine + "\nTop tasks:\n" + topTasks;
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
                    .map(m -> m.getTopicSlug() + " (Decay: " + String.format("%.2f", m.getCurrentDecayRate()) + ")")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            if (!decayingTopics.isBlank()) {
                snapshot += "\n[CRITICAL SM-2 DECAY]: " + decayingTopics;
            }
        }

        return snapshot;
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
