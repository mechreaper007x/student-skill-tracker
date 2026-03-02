package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.dto.RishiActivityBreakdownResponse;
import com.skilltracker.student_skill_tracker.dto.RishiActivityDailyTrendDto;
import com.skilltracker.student_skill_tracker.dto.RishiAttemptCategoryHeatmapDto;
import com.skilltracker.student_skill_tracker.dto.RishiAttemptCategoryTrendDto;
import com.skilltracker.student_skill_tracker.dto.RishiAttemptDailyTrendDto;
import com.skilltracker.student_skill_tracker.dto.RishiAttemptHistoryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiAttemptRecordDto;
import com.skilltracker.student_skill_tracker.dto.RishiAttemptSourceBreakdownDto;
import com.skilltracker.student_skill_tracker.dto.RishiCodeChangeBatchRequest;
import com.skilltracker.student_skill_tracker.dto.RishiCodeChangeEventDto;
import com.skilltracker.student_skill_tracker.dto.RishiCompileAttemptAnalysisResponse;
import com.skilltracker.student_skill_tracker.dto.RishiCompileAttemptRequest;
import com.skilltracker.student_skill_tracker.dto.RishiGrowthMetrics;
import com.skilltracker.student_skill_tracker.dto.RishiGrowthSummaryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiSessionEndRequest;
import com.skilltracker.student_skill_tracker.dto.RishiSessionStartRequest;
import com.skilltracker.student_skill_tracker.dto.RishiSessionStartResponse;
import com.skilltracker.student_skill_tracker.model.RishiCodeChangeEvent;
import com.skilltracker.student_skill_tracker.model.RishiCodingSession;
import com.skilltracker.student_skill_tracker.model.RishiCompileAttemptLog;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiCodeChangeEventRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCodingSessionRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCompileAttemptLogRepository;

@Service
public class RishiCodingTelemetryService {

    private static final long AFK_IDLE_TIMEOUT_MS = 120_000L;
    private static final long ACTIVE_DURATION_DRIFT_TOLERANCE_MS = 300_000L;
    private static final long MIN_ACTIVE_MS_PER_EDIT_SESSION = 1_000L;
    private static final String SOURCE_LOCAL_RUN = "local_run";
    private static final String SOURCE_DUEL_RUN = "duel_run";
    private static final String SOURCE_LEETCODE_SUBMIT = "leetcode_submit";
    private static final String FAILURE_NONE = "NONE";
    private static final String FAILURE_COMPILE = "COMPILE_FAILURE";
    private static final String FAILURE_RUNTIME = "RUNTIME_FAILURE";
    private static final String FAILURE_TEST = "TEST_FAILURE";
    private static final String FAILURE_TIMEOUT = "TIMEOUT_FAILURE";
    private static final String FAILURE_MEMORY = "MEMORY_FAILURE";
    private static final String FAILURE_OTHER = "OTHER_FAILURE";

    private final RishiCodingSessionRepository sessionRepository;
    private final RishiCodeChangeEventRepository changeEventRepository;
    private final RishiCompileAttemptLogRepository compileAttemptLogRepository;
    private final SmartQuestionRouter smartQuestionRouter;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    public RishiCodingTelemetryService(
            RishiCodingSessionRepository sessionRepository,
            RishiCodeChangeEventRepository changeEventRepository,
            RishiCompileAttemptLogRepository compileAttemptLogRepository,
            SmartQuestionRouter smartQuestionRouter,
            org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate) {
        this.sessionRepository = sessionRepository;
        this.changeEventRepository = changeEventRepository;
        this.compileAttemptLogRepository = compileAttemptLogRepository;
        this.smartQuestionRouter = smartQuestionRouter;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public RishiSessionStartResponse startSession(Student student, RishiSessionStartRequest request) {
        LocalDateTime now = LocalDateTime.now();
        RishiCodingSession session = RishiCodingSession.builder()
                .student(student)
                .language(safeLanguage(request != null ? request.getLanguage() : null))
                .problemSlug(safeProblemSlug(request != null ? request.getProblemSlug() : null))
                .startedAt(now)
                .lastActivityAt(now)
                .build();
        RishiCodingSession saved = sessionRepository.save(session);
        return RishiSessionStartResponse.builder()
                .sessionId(saved.getId())
                .startedAt(saved.getStartedAt())
                .build();
    }

    @Transactional
    public int recordChanges(Student student, Long sessionId, RishiCodeChangeBatchRequest request) {
        RishiCodingSession session = getOwnedSession(student, sessionId);
        if (session.getEndedAt() != null) {
            return 0;
        }

        List<RishiCodeChangeEventDto> incoming = request == null || request.getEvents() == null
                ? List.of()
                : request.getEvents();
        if (incoming.isEmpty()) {
            return 0;
        }

        List<RishiCodeChangeEvent> events = new ArrayList<>(incoming.size());
        int insertedTotal = 0;
        int deletedTotal = 0;
        LocalDateTime lastActivity = session.getLastActivityAt();
        Integer latestLength = session.getLatestCodeLength();

        for (RishiCodeChangeEventDto dto : incoming) {
            int inserted = clampInt(dto.getInsertedChars(), 0, 100_000);
            int deleted = clampInt(dto.getDeletedChars(), 0, 100_000);
            int rangeOffset = clampInt(dto.getRangeOffset(), 0, Integer.MAX_VALUE);
            int rangeLength = clampInt(dto.getRangeLength(), 0, Integer.MAX_VALUE);
            int resultingLength = clampInt(dto.getResultingCodeLength(), 0, 5_000_000);
            int editorVersion = clampInt(dto.getEditorVersion(), 0, Integer.MAX_VALUE);
            LocalDateTime occurredAt = parseTimestampOrNow(dto.getTimestamp());
            String activityState = normalizeActivityState(dto.getActivityState());
            Boolean editorFocused = dto.getEditorFocused();
            Boolean windowFocused = dto.getWindowFocused();
            Boolean documentVisible = dto.getDocumentVisible();

            insertedTotal += inserted;
            deletedTotal += deleted;
            latestLength = resultingLength;
            if (occurredAt.isAfter(lastActivity)) {
                lastActivity = occurredAt;
            }

            events.add(RishiCodeChangeEvent.builder()
                    .session(session)
                    .occurredAt(occurredAt)
                    .editorVersion(editorVersion)
                    .rangeOffset(rangeOffset)
                    .rangeLength(rangeLength)
                    .insertedChars(inserted)
                    .deletedChars(deleted)
                    .resultingCodeLength(resultingLength)
                    .activityState(activityState)
                    .editorFocused(editorFocused)
                    .windowFocused(windowFocused)
                    .documentVisible(documentVisible)
                    .metaJson("{}")
                    .build());
        }

        changeEventRepository.saveAll(events);

        session.setTotalChangeEvents(session.getTotalChangeEvents() + events.size());
        session.setTotalInsertedChars(session.getTotalInsertedChars() + insertedTotal);
        session.setTotalDeletedChars(session.getTotalDeletedChars() + deletedTotal);
        session.setLatestCodeLength(latestLength);
        session.setLastActivityAt(lastActivity);
        sessionRepository.save(session);

        return events.size();
    }

    @Transactional
    public RishiCompileAttemptAnalysisResponse recordCompileAttempt(Student student, Long sessionId,
            RishiCompileAttemptRequest request) {
        RishiCodingSession session = getOwnedSession(student, sessionId);
        if (session.getEndedAt() != null) {
            return RishiCompileAttemptAnalysisResponse.builder()
                    .status("ignored_session_ended")
                    .success(false)
                    .source("unknown")
                    .failureBucket(FAILURE_OTHER)
                    .mistakeCategory("SESSION_ENDED")
                    .accuracyPct(0.0)
                    .summary("Session has already ended.")
                    .nextSteps(List.of("Start a new coding session before running code."))
                    .recordedAt(LocalDateTime.now())
                    .build();
        }

        boolean success = request != null && Boolean.TRUE.equals(request.getSuccess());
        LocalDateTime now = LocalDateTime.now();
        String source = safeSource(request != null ? request.getSource() : null);
        double accuracyPct = calculateAccuracyPct(request, success);
        String mistakeCategory = classifyMistakeCategory(request, success);
        String failureBucket = classifyFailureBucket(success, mistakeCategory);
        String summary = buildAttemptSummary(source, success, mistakeCategory, accuracyPct);
        List<String> nextSteps = buildNextSteps(source, success, mistakeCategory, accuracyPct);
        String language = safeLanguage(request != null ? request.getLanguage() : session.getLanguage());
        String problemSlug = safeProblemSlug(request != null ? request.getProblemSlug() : session.getProblemSlug());
        String submissionStatus = safeText(request != null ? request.getSubmissionStatus() : null, 120);
        String judgeMessage = safeText(request != null ? request.getJudgeMessage() : null, 500);
        String errorSnippet = safeText(request != null ? request.getErrorMessage() : null, 2000);
        String outputSnippet = safeText(request != null ? request.getOutputPreview() : null, 2000);
        String failedTestInput = safeText(request != null ? request.getFailedTestInput() : null, 1000);
        String expectedOutputSnippet = safeText(request != null ? request.getExpectedOutput() : null, 1000);
        String actualOutputSnippet = safeText(request != null ? request.getActualOutput() : null, 1000);
        String stackTraceSnippet = safeText(request != null ? request.getStackTraceSnippet() : null, 2000);
        Long executionTimeMs = safeDurationMs(request != null ? request.getExecutionTimeMs() : null);
        String nextStep1 = nextSteps.size() > 0 ? nextSteps.get(0) : null;
        String nextStep2 = nextSteps.size() > 1 ? nextSteps.get(1) : null;
        String nextStep3 = nextSteps.size() > 2 ? nextSteps.get(2) : null;

        session.setCompileAttempts(session.getCompileAttempts() + 1);
        session.setLanguage(language);
        String requestSlug = safeProblemSlug(request != null ? request.getProblemSlug() : session.getProblemSlug());
        if (requestSlug != null && !requestSlug.isBlank()) {
            session.setProblemSlug(requestSlug);
        }

        if (success) {
            session.setSuccessfulCompiles(session.getSuccessfulCompiles() + 1);
            if (session.getFirstSuccessfulCompileAt() == null) {
                session.setFirstSuccessfulCompileAt(now);
                long durationMs = ChronoUnit.MILLIS.between(session.getStartedAt(), now);
                session.setFirstSuccessDurationMs(Math.max(durationMs, 0L));
            }
        } else {
            session.setFailedCompiles(session.getFailedCompiles() + 1);
        }

        session.setLastActivityAt(now);
        sessionRepository.save(session);

        RishiCompileAttemptLog attemptLog = RishiCompileAttemptLog.builder()
                .session(session)
                .attemptedAt(now)
                .attemptSource(source)
                .success(success)
                .language(language)
                .problemSlug(problemSlug)
                .executionTimeMs(executionTimeMs)
                .accuracyPct(accuracyPct)
                .mistakeCategory(mistakeCategory)
                .failureBucket(failureBucket)
                .analysisSummary(summary)
                .nextStep1(nextStep1)
                .nextStep2(nextStep2)
                .nextStep3(nextStep3)
                .submissionStatus(submissionStatus)
                .judgeMessage(judgeMessage)
                .errorSnippet(errorSnippet)
                .outputSnippet(outputSnippet)
                .failedTestInput(failedTestInput)
                .expectedOutputSnippet(expectedOutputSnippet)
                .actualOutputSnippet(actualOutputSnippet)
                .stackTraceSnippet(stackTraceSnippet)
                .sourceCodeSnapshot(safeText(request != null ? request.getSourceCode() : null, 10000))
                .build();
        compileAttemptLogRepository.save(attemptLog);

        return RishiCompileAttemptAnalysisResponse.builder()
                .status("recorded")
                .success(success)
                .source(source)
                .failureBucket(failureBucket)
                .mistakeCategory(mistakeCategory)
                .accuracyPct(round2(accuracyPct))
                .summary(summary)
                .nextSteps(nextSteps)
                .recordedAt(now)
                .build();
    }

    @Transactional
    public void endSession(Student student, Long sessionId, RishiSessionEndRequest request) {
        RishiCodingSession session = getOwnedSession(student, sessionId);
        if (session.getEndedAt() != null) {
            return;
        }

        LocalDateTime endedAt = LocalDateTime.now();
        session.setEndedAt(endedAt);
        session.setSessionEndReason(normalizeReason(request != null ? request.getReason() : null));
        session.setLastActivityAt(endedAt);

        long totalDurationMs = ChronoUnit.MILLIS.between(session.getStartedAt(), endedAt);
        session.setTotalDurationMs(Math.max(totalDurationMs, 0L));

        long estimatedActiveMs = estimateActiveDurationFromChanges(session, endedAt);
        Long requestedActiveMs = request != null ? request.getActiveDurationMs() : null;
        long resolvedActiveMs = resolveActiveDurationMs(session, requestedActiveMs, estimatedActiveMs);
        StateDurations stateDurations = resolveStateDurations(session.getTotalDurationMs(), request, resolvedActiveMs);

        session.setTypingDurationMs(stateDurations.typingMs());
        session.setCursorIdleDurationMs(stateDurations.cursorIdleMs());
        session.setEditorUnfocusedDurationMs(stateDurations.editorUnfocusedMs());
        session.setTabHiddenDurationMs(stateDurations.tabHiddenMs());
        session.setActiveDurationMs(stateDurations.activeMs());

        sessionRepository.save(session);
        sendSessionDebrief(student, session);
    }

    private void sendSessionDebrief(Student student, RishiCodingSession session) {
        String problemSlug = "general-coding";
        String topMistake = "None";

        List<RishiCompileAttemptLog> recentLogs = compileAttemptLogRepository
                .findTop5BySessionStudentOrderByAttemptedAtDesc(student);
        if (!recentLogs.isEmpty()) {
            problemSlug = recentLogs.get(0).getProblemSlug();
            topMistake = recentLogs.stream()
                    .filter(log -> !log.isSuccess() && log.getMistakeCategory() != null)
                    .map(RishiCompileAttemptLog::getMistakeCategory)
                    .findFirst()
                    .orElse("Syntax errors");
        }

        Map<String, Object> nextPick = smartQuestionRouter.recommendNext(student);
        String nextTopic = nextPick != null && nextPick.containsKey("title")
                ? String.valueOf(nextPick.get("title"))
                : "Daily Challenge";

        messagingTemplate.convertAndSend(
                "/topic/agent/" + student.getId(),
                Map.of(
                        "type", "SESSION_DEBRIEF",
                        "practiced", problemSlug,
                        "struggled", topMistake,
                        "nextUp", nextTopic,
                        "timestamp", LocalDateTime.now().toString()));
    }

    @Transactional(readOnly = true)
    public RishiGrowthSummaryResponse getGrowthSummary(Student student, int days) {
        int safeDays = Math.max(7, Math.min(60, days));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(safeDays);
        LocalDateTime previousStart = currentStart.minusDays(safeDays);

        List<RishiCodingSession> sessions = sessionRepository.findByStudentAndStartedAtBetween(student, previousStart,
                now);

        List<RishiCodingSession> current = new ArrayList<>();
        List<RishiCodingSession> previous = new ArrayList<>();

        for (RishiCodingSession session : sessions) {
            LocalDateTime marker = session.getStartedAt();
            if (!marker.isBefore(currentStart)) {
                current.add(session);
            } else if (!marker.isBefore(previousStart) && marker.isBefore(currentStart)) {
                previous.add(session);
            }
        }

        RishiGrowthMetrics currentMetrics = toMetrics(current);
        RishiGrowthMetrics previousMetrics = toMetrics(previous);

        return RishiGrowthSummaryResponse.builder()
                .days(safeDays)
                .current(currentMetrics)
                .previous(previousMetrics)
                .codingMinutesGrowthPct(
                        growthPct(currentMetrics.getTotalCodingMinutes(), previousMetrics.getTotalCodingMinutes()))
                .successRateGrowthPct(
                        growthPct(currentMetrics.getCompileSuccessRate(), previousMetrics.getCompileSuccessRate()))
                .firstSuccessSpeedGrowthPct(reverseGrowthPct(
                        currentMetrics.getAverageFirstSuccessSeconds(),
                        previousMetrics.getAverageFirstSuccessSeconds()))
                .consistencyGrowthPct(growthPct(currentMetrics.getSessions(), previousMetrics.getSessions()))
                .build();
    }

    @Transactional(readOnly = true)
    public RishiAttemptHistoryResponse getAttemptHistory(Student student, int days, int limit) {
        int safeDays = Math.max(7, Math.min(60, days));
        int safeLimit = Math.max(5, Math.min(50, limit));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(safeDays);
        LocalDateTime previousStart = currentStart.minusDays(safeDays);

        List<RishiCompileAttemptLog> currentLogs = compileAttemptLogRepository
                .findBySessionStudentAndAttemptedAtBetweenOrderByAttemptedAtDesc(student, currentStart, now);
        List<RishiCompileAttemptLog> previousLogs = compileAttemptLogRepository
                .findBySessionStudentAndAttemptedAtBetweenOrderByAttemptedAtDesc(student, previousStart, currentStart);

        long totalAttempts = currentLogs.size();
        long successfulAttempts = currentLogs.stream().filter(RishiCompileAttemptLog::isSuccess).count();
        double successRatePct = totalAttempts > 0 ? (successfulAttempts * 100.0) / totalAttempts : 0.0;
        double averageAccuracyPct = averageAccuracy(currentLogs);
        double previousAverageAccuracyPct = averageAccuracy(previousLogs);

        Map<String, Long> currentCategoryCounts = aggregateCategoryCounts(currentLogs);
        Map<String, Long> previousCategoryCounts = aggregateCategoryCounts(previousLogs);
        Set<String> categories = new HashSet<>();
        categories.addAll(currentCategoryCounts.keySet());
        categories.addAll(previousCategoryCounts.keySet());

        List<RishiAttemptCategoryTrendDto> categoryTrends = categories.stream()
                .map(category -> {
                    long currentCount = currentCategoryCounts.getOrDefault(category, 0L);
                    long previousCount = previousCategoryCounts.getOrDefault(category, 0L);
                    double sharePct = totalAttempts > 0 ? round2((currentCount * 100.0) / totalAttempts) : 0.0;
                    return RishiAttemptCategoryTrendDto.builder()
                            .category(category)
                            .currentCount(currentCount)
                            .previousCount(previousCount)
                            .sharePct(sharePct)
                            .trendPct(growthPct(currentCount, previousCount))
                            .build();
                })
                .sorted(Comparator
                        .comparingLong(RishiAttemptCategoryTrendDto::getCurrentCount).reversed()
                        .thenComparing(RishiAttemptCategoryTrendDto::getCategory))
                .toList();

        int recentCount = Math.min(safeLimit, currentLogs.size());
        List<RishiAttemptRecordDto> recentAttempts = new ArrayList<>(recentCount);
        for (int i = 0; i < recentCount; i++) {
            RishiCompileAttemptLog log = currentLogs.get(i);
            double accuracy = log.getAccuracyPct() != null ? round2(log.getAccuracyPct())
                    : (log.isSuccess() ? 100.0 : 0.0);
            String category = normalizeCategory(log.getMistakeCategory(), log.isSuccess());
            String failureBucket = normalizeFailureBucket(log.getFailureBucket(), log.isSuccess(), category);
            String summary = (log.getAnalysisSummary() == null || log.getAnalysisSummary().isBlank())
                    ? (log.isSuccess() ? "Run successful." : "Run failed. Review error details.")
                    : log.getAnalysisSummary();

            recentAttempts.add(RishiAttemptRecordDto.builder()
                    .attemptedAt(log.getAttemptedAt())
                    .source(safeSource(log.getAttemptSource()))
                    .success(log.isSuccess())
                    .failureBucket(failureBucket)
                    .accuracyPct(accuracy)
                    .mistakeCategory(category)
                    .summary(summary)
                    .nextSteps(extractNextSteps(log))
                    .build());
        }

        List<RishiAttemptDailyTrendDto> dailyTrends = buildDailyTrends(currentLogs, currentStart, now);
        List<RishiAttemptSourceBreakdownDto> sourceBreakdown = buildSourceBreakdown(currentLogs);
        List<RishiAttemptCategoryHeatmapDto> categoryHeatmap = buildCategoryHeatmap(currentLogs);

        return RishiAttemptHistoryResponse.builder()
                .days(safeDays)
                .limit(safeLimit)
                .totalAttempts(totalAttempts)
                .successfulAttempts(successfulAttempts)
                .successRatePct(round2(successRatePct))
                .averageAccuracyPct(round2(averageAccuracyPct))
                .attemptsGrowthPct(growthPct(totalAttempts, previousLogs.size()))
                .accuracyGrowthPct(growthPct(averageAccuracyPct, previousAverageAccuracyPct))
                .categoryTrends(categoryTrends)
                .recentAttempts(recentAttempts)
                .dailyTrends(dailyTrends)
                .sourceBreakdown(sourceBreakdown)
                .categoryHeatmap(categoryHeatmap)
                .build();
    }

    @Transactional(readOnly = true)
    public RishiActivityBreakdownResponse getActivityBreakdown(Student student, int days) {
        int safeDays = Math.max(7, Math.min(60, days));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(safeDays);
        List<RishiCodingSession> sessions = sessionRepository.findByStudentAndStartedAtBetween(student, start, now);

        long typingMs = 0L;
        long cursorIdleMs = 0L;
        long editorUnfocusedMs = 0L;
        long tabHiddenMs = 0L;
        Map<LocalDate, StateDurations> daily = new LinkedHashMap<>();

        for (RishiCodingSession session : sessions) {
            long sessionTotalMs = safeDurationMs(session.getTotalDurationMs());
            StateDurations sessionDurations = resolveStateDurationsFromSession(session, sessionTotalMs);

            typingMs += sessionDurations.typingMs();
            cursorIdleMs += sessionDurations.cursorIdleMs();
            editorUnfocusedMs += sessionDurations.editorUnfocusedMs();
            tabHiddenMs += sessionDurations.tabHiddenMs();

            LocalDate day = session.getStartedAt() == null ? LocalDate.now() : session.getStartedAt().toLocalDate();
            StateDurations existing = daily.get(day);
            if (existing == null) {
                daily.put(day, sessionDurations);
            } else {
                daily.put(day, new StateDurations(
                        existing.typingMs() + sessionDurations.typingMs(),
                        existing.cursorIdleMs() + sessionDurations.cursorIdleMs(),
                        existing.editorUnfocusedMs() + sessionDurations.editorUnfocusedMs(),
                        existing.tabHiddenMs() + sessionDurations.tabHiddenMs(),
                        existing.activeMs() + sessionDurations.activeMs()));
            }
        }

        long activeMs = typingMs + cursorIdleMs;
        long totalTrackedMs = typingMs + cursorIdleMs + editorUnfocusedMs + tabHiddenMs;

        List<RishiActivityDailyTrendDto> dailyTrends = daily.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    StateDurations value = entry.getValue();
                    return RishiActivityDailyTrendDto.builder()
                            .date(entry.getKey().toString())
                            .typingMinutes(msToMinutes(value.typingMs()))
                            .cursorIdleMinutes(msToMinutes(value.cursorIdleMs()))
                            .editorUnfocusedMinutes(msToMinutes(value.editorUnfocusedMs()))
                            .tabHiddenMinutes(msToMinutes(value.tabHiddenMs()))
                            .activeMinutes(msToMinutes(value.activeMs()))
                            .build();
                })
                .toList();

        return RishiActivityBreakdownResponse.builder()
                .days(safeDays)
                .typingMinutes(msToMinutes(typingMs))
                .cursorIdleMinutes(msToMinutes(cursorIdleMs))
                .editorUnfocusedMinutes(msToMinutes(editorUnfocusedMs))
                .tabHiddenMinutes(msToMinutes(tabHiddenMs))
                .activeMinutes(msToMinutes(activeMs))
                .totalTrackedMinutes(msToMinutes(totalTrackedMs))
                .typingSharePct(sharePct(typingMs, totalTrackedMs))
                .cursorIdleSharePct(sharePct(cursorIdleMs, totalTrackedMs))
                .editorUnfocusedSharePct(sharePct(editorUnfocusedMs, totalTrackedMs))
                .tabHiddenSharePct(sharePct(tabHiddenMs, totalTrackedMs))
                .dailyTrends(dailyTrends)
                .build();
    }

    @Transactional(readOnly = true)
    public String exportAttemptHistoryCsv(Student student, int days) {
        int safeDays = Math.max(7, Math.min(60, days));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(safeDays);
        List<RishiCompileAttemptLog> logs = compileAttemptLogRepository
                .findBySessionStudentAndAttemptedAtBetweenOrderByAttemptedAtDesc(student, start, now);

        StringJoiner csv = new StringJoiner("\n");
        csv.add("attemptedAt,source,success,failureBucket,accuracyPct,mistakeCategory,summary,nextStep1,nextStep2,nextStep3,errorSnippet,submissionStatus,judgeMessage");
        for (RishiCompileAttemptLog log : logs) {
            String category = normalizeCategory(log.getMistakeCategory(), log.isSuccess());
            String bucket = normalizeFailureBucket(log.getFailureBucket(), log.isSuccess(), category);
            csv.add(String.join(",",
                    csvValue(log.getAttemptedAt() == null ? "" : log.getAttemptedAt().toString()),
                    csvValue(safeSource(log.getAttemptSource())),
                    csvValue(Boolean.toString(log.isSuccess())),
                    csvValue(bucket),
                    csvValue(Double.toString(round2(
                            log.getAccuracyPct() == null ? (log.isSuccess() ? 100.0 : 0.0) : log.getAccuracyPct()))),
                    csvValue(category),
                    csvValue(log.getAnalysisSummary()),
                    csvValue(log.getNextStep1()),
                    csvValue(log.getNextStep2()),
                    csvValue(log.getNextStep3()),
                    csvValue(log.getErrorSnippet()),
                    csvValue(log.getSubmissionStatus()),
                    csvValue(log.getJudgeMessage())));
        }
        return csv.toString();
    }

    private RishiGrowthMetrics toMetrics(List<RishiCodingSession> sessions) {
        if (sessions.isEmpty()) {
            return RishiGrowthMetrics.builder()
                    .sessions(0)
                    .totalCodingMinutes(0L)
                    .compileAttempts(0)
                    .compileSuccessRate(0.0)
                    .averageFirstSuccessSeconds(0.0)
                    .averageEditsPerSession(0.0)
                    .build();
        }

        long totalDurationMs = 0L;
        int compileAttempts = 0;
        int successfulCompiles = 0;
        long totalEdits = 0L;
        long firstSuccessCount = 0L;
        long firstSuccessDurationMs = 0L;

        for (RishiCodingSession session : sessions) {
            totalDurationMs += resolveDurationForMetrics(session);
            compileAttempts += session.getCompileAttempts() != null ? session.getCompileAttempts() : 0;
            successfulCompiles += session.getSuccessfulCompiles() != null ? session.getSuccessfulCompiles() : 0;
            totalEdits += session.getTotalChangeEvents() != null ? session.getTotalChangeEvents() : 0;

            if (session.getFirstSuccessDurationMs() != null && session.getFirstSuccessDurationMs() > 0) {
                firstSuccessCount++;
                firstSuccessDurationMs += session.getFirstSuccessDurationMs();
            }
        }

        double successRate = compileAttempts > 0
                ? (successfulCompiles * 100.0) / compileAttempts
                : 0.0;
        double avgFirstSuccessSec = firstSuccessCount > 0
                ? (firstSuccessDurationMs / (double) firstSuccessCount) / 1000.0
                : 0.0;
        double avgEditsPerSession = totalEdits / (double) sessions.size();

        return RishiGrowthMetrics.builder()
                .sessions(sessions.size())
                .totalCodingMinutes(Math.round(totalDurationMs / 60000.0))
                .compileAttempts(compileAttempts)
                .compileSuccessRate(round2(successRate))
                .averageFirstSuccessSeconds(round2(avgFirstSuccessSec))
                .averageEditsPerSession(round2(avgEditsPerSession))
                .build();
    }

    private static double averageAccuracy(List<RishiCompileAttemptLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (RishiCompileAttemptLog log : logs) {
            if (log.getAccuracyPct() != null) {
                total += log.getAccuracyPct();
            } else {
                total += log.isSuccess() ? 100.0 : 0.0;
            }
        }
        return total / logs.size();
    }

    private static Map<String, Long> aggregateCategoryCounts(List<RishiCompileAttemptLog> logs) {
        Map<String, Long> counts = new HashMap<>();
        if (logs == null) {
            return counts;
        }

        for (RishiCompileAttemptLog log : logs) {
            String category = normalizeCategory(log.getMistakeCategory(), log.isSuccess());
            counts.put(category, counts.getOrDefault(category, 0L) + 1L);
        }
        return counts;
    }

    private static String normalizeCategory(String category, boolean success) {
        if (category == null || category.isBlank()) {
            return success ? "NONE" : "UNKNOWN";
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }

    private static List<String> extractNextSteps(RishiCompileAttemptLog log) {
        List<String> steps = new ArrayList<>(3);
        if (log.getNextStep1() != null && !log.getNextStep1().isBlank()) {
            steps.add(log.getNextStep1());
        }
        if (log.getNextStep2() != null && !log.getNextStep2().isBlank()) {
            steps.add(log.getNextStep2());
        }
        if (log.getNextStep3() != null && !log.getNextStep3().isBlank()) {
            steps.add(log.getNextStep3());
        }
        return steps;
    }

    private RishiCodingSession getOwnedSession(Student student, Long sessionId) {
        return sessionRepository.findByIdAndStudent(sessionId, student)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    private static String safeLanguage(String language) {
        if (language == null) {
            return null;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 32) {
            return normalized.substring(0, 32);
        }
        return normalized;
    }

    private static String safeProblemSlug(String slug) {
        if (slug == null) {
            return null;
        }
        String normalized = slug.trim();
        if (normalized.length() > 255) {
            return normalized.substring(0, 255);
        }
        return normalized;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "manual";
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }

    private static LocalDateTime parseTimestampOrNow(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(timestamp);
            } catch (Exception ignoredAgain) {
                return LocalDateTime.now(ZoneOffset.UTC);
            }
        }
    }

    private static String safeSource(String source) {
        if (source == null || source.isBlank()) {
            return SOURCE_LOCAL_RUN;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }

    private static String safeText(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.trim().replaceAll("\\s+", " ");
        if (compact.length() > maxLen) {
            return compact.substring(0, maxLen);
        }
        return compact;
    }

    private static double calculateAccuracyPct(RishiCompileAttemptRequest request, boolean success) {
        if (request != null && request.getTestsTotal() != null && request.getTestsTotal() > 0) {
            int total = Math.max(1, request.getTestsTotal());
            int passed = Math.max(0, Math.min(total, request.getTestsPassed() == null ? 0 : request.getTestsPassed()));
            return round2((passed * 100.0) / total);
        }

        String submissionStatus = normalizeStatus(request != null ? request.getSubmissionStatus() : null);
        if ("accepted".equals(submissionStatus)) {
            return 100.0;
        }
        return success ? 100.0 : 0.0;
    }

    private static String classifyMistakeCategory(RishiCompileAttemptRequest request, boolean success) {
        if (success) {
            return "NONE";
        }

        String status = normalizeStatus(request != null ? request.getSubmissionStatus() : null);
        String haystack = String.join(" ",
                Objects.toString(request != null ? request.getErrorMessage() : "", ""),
                Objects.toString(request != null ? request.getJudgeMessage() : "", ""),
                Objects.toString(request != null ? request.getOutputPreview() : "", ""),
                status).toLowerCase(Locale.ROOT);

        if (status.contains("wrong answer") || haystack.contains("wrong answer")) {
            return "WRONG_ANSWER";
        }
        if (status.contains("time limit exceeded") || haystack.contains("time limit exceeded")
                || haystack.contains("timeout")) {
            return "TIME_LIMIT_EXCEEDED";
        }
        if (status.contains("memory limit exceeded") || haystack.contains("memory limit exceeded")) {
            return "MEMORY_LIMIT_EXCEEDED";
        }
        if (status.contains("runtime error") || haystack.contains("runtime error")
                || haystack.contains("exception") || haystack.contains("segmentation fault")
                || haystack.contains("nullpointer")) {
            return "RUNTIME_ERROR";
        }
        if (status.contains("compile error") || haystack.contains("compilation error")
                || haystack.contains("compile error") || haystack.contains("syntax")
                || haystack.contains("cannot find symbol")) {
            return "COMPILATION_ERROR";
        }
        return "LOGIC_OR_EDGE_CASE";
    }

    private static String buildAttemptSummary(String source, boolean success, String mistakeCategory,
            double accuracyPct) {
        if (success) {
            if (accuracyPct >= 100.0) {
                return source.equals(SOURCE_LEETCODE_SUBMIT)
                        ? "Submission accepted. Accuracy is 100%."
                        : "Local run succeeded. Accuracy is currently 100%.";
            }
            return "Run completed with partial accuracy. Some cases are still failing.";
        }

        return switch (mistakeCategory) {
            case "COMPILATION_ERROR" -> "Build failed before execution. There are syntax or type issues to fix first.";
            case "RUNTIME_ERROR" -> "Code crashed at runtime. Input handling or edge-case guards are missing.";
            case "TIME_LIMIT_EXCEEDED" -> "Execution exceeded time limits. Algorithmic complexity must be reduced.";
            case "MEMORY_LIMIT_EXCEEDED" ->
                "Execution exceeded memory limits. Data structures or allocations are too heavy.";
            case "WRONG_ANSWER" -> "Program ran but produced incorrect output. Logic and edge cases need correction.";
            default -> "Attempt failed due to logic/edge-case issues. Validate assumptions against tricky inputs.";
        };
    }

    private static List<String> buildNextSteps(String source, boolean success, String mistakeCategory,
            double accuracyPct) {
        if (success && accuracyPct >= 100.0) {
            if (SOURCE_LEETCODE_SUBMIT.equals(source)) {
                return List.of(
                        "Write down the key pattern used in this solution.",
                        "Run one adversarial edge case to ensure it is robust.",
                        "Move to the next problem with similar constraints.");
            }
            return List.of(
                    "Mirror this with 3 edge-case tests (empty, single-element, max input).",
                    "Review complexity and see if one optimization is possible.",
                    "Submit to LeetCode when local behavior is stable.");
        }

        if (success) {
            return List.of(
                    "List failing scenarios and reproduce each one locally.",
                    "Instrument intermediate values around the failure path.",
                    "Adjust logic for edge cases, then rerun full tests.");
        }

        return switch (mistakeCategory) {
            case "COMPILATION_ERROR" -> List.of(
                    "Fix the first compiler error only, then rerun.",
                    "Verify method signatures, imports, and variable scope.",
                    "After build passes, validate with a minimal sample input.");
            case "RUNTIME_ERROR" -> List.of(
                    "Reproduce crash with the smallest failing input.",
                    "Add null/bounds guards before unsafe access.",
                    "Log intermediate state near the crash point and rerun.");
            case "TIME_LIMIT_EXCEEDED" -> List.of(
                    "Identify nested loops or repeated scans causing O(n^2+) behavior.",
                    "Replace brute force with hashing/two-pointer/prefix strategy.",
                    "Rerun on large inputs to confirm time improvement.");
            case "MEMORY_LIMIT_EXCEEDED" -> List.of(
                    "Remove unnecessary copies of arrays/strings/collections.",
                    "Switch to in-place or streaming processing where possible.",
                    "Validate memory footprint on larger input size.");
            case "WRONG_ANSWER" -> List.of(
                    "Compare expected vs actual output on custom edge cases.",
                    "Recheck boundary conditions (0, 1, duplicates, negatives, max).",
                    "Refactor core condition logic and rerun.");
            default -> List.of(
                    "Split solution into smaller verifiable steps and test each step.",
                    "Add asserts or debug prints around decision branches.",
                    "Rerun with adversarial inputs to isolate logic gaps.");
        };
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private long estimateActiveDurationFromChanges(RishiCodingSession session, LocalDateTime endedAt) {
        List<RishiCodeChangeEvent> events = changeEventRepository.findBySessionOrderByOccurredAtAsc(session);
        if (events.isEmpty()) {
            return 0L;
        }

        long estimatedActiveMs = MIN_ACTIVE_MS_PER_EDIT_SESSION;
        LocalDateTime previous = events.get(0).getOccurredAt();

        for (int i = 1; i < events.size(); i++) {
            LocalDateTime current = events.get(i).getOccurredAt();
            long gapMs = ChronoUnit.MILLIS.between(previous, current);
            if (gapMs > 0) {
                estimatedActiveMs += Math.min(gapMs, AFK_IDLE_TIMEOUT_MS);
            }
            if (current.isAfter(previous)) {
                previous = current;
            }
        }

        LocalDateTime boundary = endedAt != null ? endedAt : LocalDateTime.now();
        long tailGapMs = ChronoUnit.MILLIS.between(previous, boundary);
        if (tailGapMs > 0) {
            estimatedActiveMs += Math.min(tailGapMs, AFK_IDLE_TIMEOUT_MS);
        }

        long totalDurationMs = safeDurationMs(session.getTotalDurationMs());
        if (totalDurationMs == 0L) {
            return Math.max(0L, estimatedActiveMs);
        }
        return Math.max(0L, Math.min(estimatedActiveMs, totalDurationMs));
    }

    private long resolveActiveDurationMs(RishiCodingSession session, Long requestedActiveMs, long estimatedActiveMs) {
        int totalChangeEvents = session.getTotalChangeEvents() == null ? 0
                : Math.max(0, session.getTotalChangeEvents());
        if (totalChangeEvents == 0) {
            return 0L;
        }

        long totalDurationMs = safeDurationMs(session.getTotalDurationMs());
        long sanitizedEstimated = Math.min(Math.max(0L, estimatedActiveMs), totalDurationMs);

        if (requestedActiveMs == null || requestedActiveMs <= 0L) {
            return sanitizedEstimated;
        }

        long sanitizedRequested = Math.min(Math.max(0L, requestedActiveMs), totalDurationMs);
        if (sanitizedEstimated <= 0L) {
            return sanitizedRequested;
        }

        long maxAllowedByEvents = Math.min(totalDurationMs, sanitizedEstimated + ACTIVE_DURATION_DRIFT_TOLERANCE_MS);
        return Math.min(sanitizedRequested, maxAllowedByEvents);
    }

    private long resolveDurationForMetrics(RishiCodingSession session) {
        long activeDurationMs = safeDurationMs(session.getActiveDurationMs());
        if (activeDurationMs > 0L) {
            return activeDurationMs;
        }

        int totalChangeEvents = session.getTotalChangeEvents() == null ? 0
                : Math.max(0, session.getTotalChangeEvents());
        if (totalChangeEvents == 0) {
            return 0L;
        }

        return safeDurationMs(session.getTotalDurationMs());
    }

    private List<RishiAttemptDailyTrendDto> buildDailyTrends(
            List<RishiCompileAttemptLog> logs,
            LocalDateTime start,
            LocalDateTime end) {
        Map<LocalDate, List<RishiCompileAttemptLog>> byDay = new LinkedHashMap<>();
        for (RishiCompileAttemptLog log : logs) {
            LocalDate day = log.getAttemptedAt() == null ? LocalDate.now() : log.getAttemptedAt().toLocalDate();
            byDay.computeIfAbsent(day, ignored -> new ArrayList<>()).add(log);
        }

        List<RishiAttemptDailyTrendDto> trends = new ArrayList<>();
        LocalDate cursor = start.toLocalDate();
        LocalDate finalDay = end.toLocalDate();
        while (!cursor.isAfter(finalDay)) {
            List<RishiCompileAttemptLog> dayLogs = byDay.getOrDefault(cursor, List.of());
            long attempts = dayLogs.size();
            long successful = dayLogs.stream().filter(RishiCompileAttemptLog::isSuccess).count();
            double successRatePct = attempts > 0 ? (successful * 100.0) / attempts : 0.0;
            double averageAccuracyPct = averageAccuracy(dayLogs);
            trends.add(RishiAttemptDailyTrendDto.builder()
                    .date(cursor.toString())
                    .attempts(attempts)
                    .successRatePct(round2(successRatePct))
                    .averageAccuracyPct(round2(averageAccuracyPct))
                    .build());
            cursor = cursor.plusDays(1);
        }
        return trends;
    }

    private List<RishiAttemptSourceBreakdownDto> buildSourceBreakdown(List<RishiCompileAttemptLog> logs) {
        Map<String, List<RishiCompileAttemptLog>> bySource = new HashMap<>();
        for (RishiCompileAttemptLog log : logs) {
            String source = safeSource(log.getAttemptSource());
            bySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(log);
        }

        return bySource.entrySet().stream()
                .map(entry -> {
                    List<RishiCompileAttemptLog> sourceLogs = entry.getValue();
                    long attempts = sourceLogs.size();
                    long successful = sourceLogs.stream().filter(RishiCompileAttemptLog::isSuccess).count();
                    double successRatePct = attempts > 0 ? (successful * 100.0) / attempts : 0.0;
                    return RishiAttemptSourceBreakdownDto.builder()
                            .source(entry.getKey())
                            .attempts(attempts)
                            .successfulAttempts(successful)
                            .successRatePct(round2(successRatePct))
                            .averageAccuracyPct(round2(averageAccuracy(sourceLogs)))
                            .build();
                })
                .sorted(Comparator.comparingLong(RishiAttemptSourceBreakdownDto::getAttempts).reversed()
                        .thenComparing(RishiAttemptSourceBreakdownDto::getSource))
                .toList();
    }

    private List<RishiAttemptCategoryHeatmapDto> buildCategoryHeatmap(List<RishiCompileAttemptLog> logs) {
        record CellKey(String category, String source) {
        }
        Map<CellKey, long[]> aggregate = new HashMap<>();
        for (RishiCompileAttemptLog log : logs) {
            String category = normalizeCategory(log.getMistakeCategory(), log.isSuccess());
            String source = safeSource(log.getAttemptSource());
            CellKey key = new CellKey(category, source);
            long[] counters = aggregate.computeIfAbsent(key, ignored -> new long[2]);
            counters[0] += 1L;
            if (!log.isSuccess()) {
                counters[1] += 1L;
            }
        }

        return aggregate.entrySet().stream()
                .map(entry -> RishiAttemptCategoryHeatmapDto.builder()
                        .category(entry.getKey().category())
                        .source(entry.getKey().source())
                        .attempts(entry.getValue()[0])
                        .failedAttempts(entry.getValue()[1])
                        .build())
                .sorted(Comparator.comparingLong(RishiAttemptCategoryHeatmapDto::getAttempts).reversed()
                        .thenComparing(RishiAttemptCategoryHeatmapDto::getCategory)
                        .thenComparing(RishiAttemptCategoryHeatmapDto::getSource))
                .toList();
    }

    private static StateDurations resolveStateDurations(Long totalDurationMsRaw, RishiSessionEndRequest request,
            long activeDurationMs) {
        long totalDurationMs = safeDurationMs(totalDurationMsRaw);
        long typingMs = safeDurationMs(request != null ? request.getTypingDurationMs() : null);
        long cursorIdleMs = safeDurationMs(request != null ? request.getCursorIdleDurationMs() : null);
        long editorUnfocusedMs = safeDurationMs(request != null ? request.getEditorUnfocusedDurationMs() : null);
        long tabHiddenMs = safeDurationMs(request != null ? request.getTabHiddenDurationMs() : null);

        long providedTotal = typingMs + cursorIdleMs + editorUnfocusedMs + tabHiddenMs;
        if (providedTotal <= 0L) {
            long fallbackActiveMs = Math.min(Math.max(activeDurationMs, 0L), totalDurationMs);
            long fallbackInactiveMs = Math.max(0L, totalDurationMs - fallbackActiveMs);
            return new StateDurations(fallbackActiveMs, 0L, fallbackInactiveMs, 0L, fallbackActiveMs);
        }

        if (providedTotal > totalDurationMs && providedTotal > 0L) {
            double ratio = totalDurationMs / (double) providedTotal;
            typingMs = Math.round(typingMs * ratio);
            cursorIdleMs = Math.round(cursorIdleMs * ratio);
            editorUnfocusedMs = Math.round(editorUnfocusedMs * ratio);
            tabHiddenMs = Math.round(tabHiddenMs * ratio);
        }

        long activeMs = Math.min(totalDurationMs, typingMs + cursorIdleMs);
        return new StateDurations(
                Math.max(0L, typingMs),
                Math.max(0L, cursorIdleMs),
                Math.max(0L, editorUnfocusedMs),
                Math.max(0L, tabHiddenMs),
                Math.max(0L, activeMs));
    }

    private static StateDurations resolveStateDurationsFromSession(RishiCodingSession session, long totalDurationMs) {
        long typingMs = safeDurationMs(session.getTypingDurationMs());
        long cursorIdleMs = safeDurationMs(session.getCursorIdleDurationMs());
        long editorUnfocusedMs = safeDurationMs(session.getEditorUnfocusedDurationMs());
        long tabHiddenMs = safeDurationMs(session.getTabHiddenDurationMs());

        long trackedTotal = typingMs + cursorIdleMs + editorUnfocusedMs + tabHiddenMs;
        if (trackedTotal <= 0L) {
            long activeMs = Math.min(safeDurationMs(session.getActiveDurationMs()), totalDurationMs);
            long inactiveMs = Math.max(0L, totalDurationMs - activeMs);
            return new StateDurations(activeMs, 0L, inactiveMs, 0L, activeMs);
        }

        if (trackedTotal > totalDurationMs && trackedTotal > 0L) {
            double ratio = totalDurationMs / (double) trackedTotal;
            typingMs = Math.round(typingMs * ratio);
            cursorIdleMs = Math.round(cursorIdleMs * ratio);
            editorUnfocusedMs = Math.round(editorUnfocusedMs * ratio);
            tabHiddenMs = Math.round(tabHiddenMs * ratio);
        }

        long activeMs = Math.min(totalDurationMs, typingMs + cursorIdleMs);
        return new StateDurations(typingMs, cursorIdleMs, editorUnfocusedMs, tabHiddenMs, activeMs);
    }

    private static String normalizeActivityState(String activityState) {
        if (activityState == null || activityState.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = activityState.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.length() > 40) {
            return normalized.substring(0, 40);
        }
        return normalized;
    }

    private static String classifyFailureBucket(boolean success, String mistakeCategory) {
        if (success) {
            return FAILURE_NONE;
        }
        if ("COMPILATION_ERROR".equals(mistakeCategory)) {
            return FAILURE_COMPILE;
        }
        if ("RUNTIME_ERROR".equals(mistakeCategory)) {
            return FAILURE_RUNTIME;
        }
        if ("TIME_LIMIT_EXCEEDED".equals(mistakeCategory)) {
            return FAILURE_TIMEOUT;
        }
        if ("MEMORY_LIMIT_EXCEEDED".equals(mistakeCategory)) {
            return FAILURE_MEMORY;
        }
        if ("WRONG_ANSWER".equals(mistakeCategory) || "LOGIC_OR_EDGE_CASE".equals(mistakeCategory)) {
            return FAILURE_TEST;
        }
        return FAILURE_OTHER;
    }

    private static String normalizeFailureBucket(String bucket, boolean success, String mistakeCategory) {
        if (bucket == null || bucket.isBlank()) {
            return classifyFailureBucket(success, mistakeCategory);
        }
        String normalized = bucket.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) {
            return normalized.substring(0, 64);
        }
        return normalized;
    }

    private static long msToMinutes(long value) {
        return Math.max(0L, Math.round(Math.max(0L, value) / 60000.0));
    }

    private static double sharePct(long part, long total) {
        if (total <= 0L) {
            return 0.0;
        }
        return round2((part * 100.0) / total);
    }

    private static String csvValue(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private record StateDurations(
            long typingMs,
            long cursorIdleMs,
            long editorUnfocusedMs,
            long tabHiddenMs,
            long activeMs) {
    }

    private static long safeDurationMs(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static int clampInt(Integer value, int min, int max) {
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double growthPct(double current, double previous) {
        if (previous <= 0.0) {
            return current > 0.0 ? 100.0 : 0.0;
        }
        return round2(((current - previous) / previous) * 100.0);
    }

    // Lower first-success time is better, so we invert the sign.
    private static double reverseGrowthPct(double current, double previous) {
        if (previous <= 0.0) {
            return current > 0.0 ? -100.0 : 0.0;
        }
        return round2(((previous - current) / previous) * 100.0);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
