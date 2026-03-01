package com.skilltracker.student_skill_tracker.service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.skilltracker.student_skill_tracker.dto.RishiIntegrationStatusResponse;
import com.skilltracker.student_skill_tracker.dto.RishiCoachingSummaryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiCodeforcesAnalyticsDto;
import com.skilltracker.student_skill_tracker.dto.RishiFocusMetricsDto;
import com.skilltracker.student_skill_tracker.dto.RishiGithubAnalyticsDto;
import com.skilltracker.student_skill_tracker.dto.RishiLeetCodeAnalyticsDto;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthCompleteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthUrlResponse;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleItemDto;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleRequest;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleResponse;
import com.skilltracker.student_skill_tracker.dto.RishiTaskDto;
import com.skilltracker.student_skill_tracker.dto.RishiTogglFocusDto;
import com.skilltracker.student_skill_tracker.model.RishiCodeforcesAnalyticsSnapshot;
import com.skilltracker.student_skill_tracker.model.RishiCodingSession;
import com.skilltracker.student_skill_tracker.model.RishiGithubAnalyticsSnapshot;
import com.skilltracker.student_skill_tracker.model.RishiLeetCodeAnalyticsSnapshot;
import com.skilltracker.student_skill_tracker.model.RishiPracticeTask;
import com.skilltracker.student_skill_tracker.model.RishiTogglFocusSnapshot;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiCodeforcesAnalyticsSnapshotRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCodingSessionRepository;
import com.skilltracker.student_skill_tracker.repository.RishiGithubAnalyticsSnapshotRepository;
import com.skilltracker.student_skill_tracker.repository.RishiLeetCodeAnalyticsSnapshotRepository;
import com.skilltracker.student_skill_tracker.repository.RishiPracticeTaskRepository;
import com.skilltracker.student_skill_tracker.repository.RishiTogglFocusSnapshotRepository;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;

@Service
public class RishiIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(RishiIntegrationService.class);

    private static final String MODE_CHAT = "chat";
    private static final String MODE_AGENT = "agent";
    private static final String STATUS_TODO = "TODO";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_MISSED = "MISSED";
    private static final String SOURCE_TYPE_AUTO = "SYSTEM_AUTO";
    private static final int INTEGRATION_RETRY_MAX_ATTEMPTS = 3;
    private static final long INTEGRATION_RETRY_BASE_DELAY_MS = 300L;
    private static final int DEFAULT_GITHUB_WINDOW_DAYS = 30;
    private static final int MIN_GITHUB_WINDOW_DAYS = 7;
    private static final int MAX_GITHUB_WINDOW_DAYS = 180;
    private static final int DEFAULT_LEETCODE_WINDOW_DAYS = 30;
    private static final int MIN_LEETCODE_WINDOW_DAYS = 14;
    private static final int MAX_LEETCODE_WINDOW_DAYS = 180;
    private static final int DEFAULT_CODEFORCES_WINDOW_DAYS = 30;
    private static final int MIN_CODEFORCES_WINDOW_DAYS = 14;
    private static final int MAX_CODEFORCES_WINDOW_DAYS = 180;
    private static final int MAX_CODEFORCES_SUBMISSIONS_FETCH = 1000;
    private static final int DEFAULT_TOGGL_WINDOW_DAYS = 7;
    private static final int MIN_TOGGL_WINDOW_DAYS = 3;
    private static final int MAX_TOGGL_WINDOW_DAYS = 30;
    private static final int DEFAULT_FOCUS_WINDOW_DAYS = 7;

    private final RestTemplate restTemplate;
    private final TokenCryptoService tokenCryptoService;
    private final LeetCodeService leetCodeService;
    private final StudentRepository studentRepository;
    private final RishiPracticeTaskRepository taskRepository;
    private final RishiCodingSessionRepository codingSessionRepository;
    private final RishiCodeforcesAnalyticsSnapshotRepository codeforcesAnalyticsSnapshotRepository;
    private final RishiGithubAnalyticsSnapshotRepository githubAnalyticsSnapshotRepository;
    private final RishiLeetCodeAnalyticsSnapshotRepository leetCodeAnalyticsSnapshotRepository;
    private final RishiTogglFocusSnapshotRepository togglFocusSnapshotRepository;

    @Value("${rishi.integrations.google.client-id:}")
    private String googleClientId;

    @Value("${rishi.integrations.google.client-secret:}")
    private String googleClientSecret;

    @Value("${rishi.integrations.google.calendar-id:primary}")
    private String defaultGoogleCalendarId;

    public RishiIntegrationService(
            RestTemplate restTemplate,
            TokenCryptoService tokenCryptoService,
            LeetCodeService leetCodeService,
            StudentRepository studentRepository,
            RishiPracticeTaskRepository taskRepository,
            RishiCodingSessionRepository codingSessionRepository,
            RishiCodeforcesAnalyticsSnapshotRepository codeforcesAnalyticsSnapshotRepository,
            RishiGithubAnalyticsSnapshotRepository githubAnalyticsSnapshotRepository,
            RishiLeetCodeAnalyticsSnapshotRepository leetCodeAnalyticsSnapshotRepository,
            RishiTogglFocusSnapshotRepository togglFocusSnapshotRepository) {
        this.restTemplate = restTemplate;
        this.tokenCryptoService = tokenCryptoService;
        this.leetCodeService = leetCodeService;
        this.studentRepository = studentRepository;
        this.taskRepository = taskRepository;
        this.codingSessionRepository = codingSessionRepository;
        this.codeforcesAnalyticsSnapshotRepository = codeforcesAnalyticsSnapshotRepository;
        this.githubAnalyticsSnapshotRepository = githubAnalyticsSnapshotRepository;
        this.leetCodeAnalyticsSnapshotRepository = leetCodeAnalyticsSnapshotRepository;
        this.togglFocusSnapshotRepository = togglFocusSnapshotRepository;
    }

    public RishiIntegrationStatusResponse getStatus(Student student) {
        List<RishiPracticeTask> tasks = taskRepository.findByStudentOrderByUpdatedAtDesc(student);
        int pendingCount = (int) tasks.stream()
                .filter(task -> !STATUS_DONE.equalsIgnoreCase(task.getStatus()))
                .count();
        Optional<RishiCodeforcesAnalyticsDto> latestCodeforcesAnalytics = getLatestCodeforcesAnalytics(student);
        Optional<RishiGithubAnalyticsDto> latestGithubAnalytics = getLatestGithubAnalytics(student);
        Optional<RishiLeetCodeAnalyticsDto> latestLeetCodeAnalytics = getLatestLeetCodeAnalytics(student);
        Optional<RishiTogglFocusDto> latestTogglFocus = getLatestTogglFocus(student);
        RishiFocusMetricsDto focusMetrics = getFocusMetrics(student, DEFAULT_FOCUS_WINDOW_DAYS);

        List<RishiTaskDto> latest = tasks.stream()
                .sorted(Comparator.comparing(RishiPracticeTask::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(this::toTaskDto)
                .toList();

        return RishiIntegrationStatusResponse.builder()
                .mode(normalizeMode(student.getRishiMode()))
                .hasApiKey(!isBlank(student.getAiApiKeyEncrypted()))
                .googleCalendarConnected(isGoogleCalendarConnected(student))
                .githubConnected(isGithubConnected(student))
                .leetcodeConnected(isLeetCodeConnected(student))
                .codeforcesConnected(isCodeforcesConnected(student))
                .togglConnected(isTogglConnected(student))
                .googleCalendarId(nullToEmpty(student.getRishiGoogleCalendarId()))
                .codeforcesHandle(nullToEmpty(student.getCodeforcesHandle()))
                .taskCount(tasks.size())
                .pendingTaskCount(pendingCount)
                .latestCodeforcesAnalytics(latestCodeforcesAnalytics.orElse(null))
                .latestGithubAnalytics(latestGithubAnalytics.orElse(null))
                .latestLeetCodeAnalytics(latestLeetCodeAnalytics.orElse(null))
                .latestTogglFocus(latestTogglFocus.orElse(null))
                .focusMetrics(focusMetrics)
                .latestTasks(latest)
                .build();
    }

    @Transactional
    public String setMode(Student student, String requestedMode) {
        String mode = normalizeMode(requestedMode);
        student.setRishiMode(mode);
        studentRepository.save(student);
        return mode;
    }

    @Transactional
    public RishiOAuthUrlResponse createGoogleAuthUrl(Student student, String redirectUri) {
        requireConfigured(googleClientId, "Google client ID");
        requireConfigured(googleClientSecret, "Google client secret");
        requireConfigured(redirectUri, "redirectUri");

        String state = "google_" + UUID.randomUUID();
        student.setRishiGoogleOauthState(state);
        studentRepository.save(student);

        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri.trim())
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("scope", "openid email profile https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/calendar.events")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();

        return RishiOAuthUrlResponse.builder()
                .provider("google-calendar")
                .authUrl(uri.toString())
                .state(state)
                .build();
    }

    @Transactional
    public String completeOAuth(Student student, RishiOAuthCompleteRequest request) {
        String code = request == null ? "" : nullToEmpty(request.getCode()).trim();
        String state = request == null ? "" : nullToEmpty(request.getState()).trim();
        String redirectUri = request == null ? "" : nullToEmpty(request.getRedirectUri()).trim();

        if (isBlank(code) || isBlank(state) || isBlank(redirectUri)) {
            throw new IllegalArgumentException("code, state, and redirectUri are required");
        }

        if (!state.equals(student.getRishiGoogleOauthState())) {
            throw new IllegalArgumentException("Invalid oauth state.");
        }

        exchangeGoogleAuthorizationCode(student, code, redirectUri);
        student.setRishiGoogleOauthState(null);
        studentRepository.save(student);
        return "google-calendar";
    }

    @Transactional
    public void disconnectGoogleCalendar(Student student) {
        student.setRishiGoogleOauthState(null);
        student.setRishiGoogleAccessTokenEncrypted(null);
        student.setRishiGoogleRefreshTokenEncrypted(null);
        student.setRishiGoogleTokenExpiresAt(null);
        student.setRishiGoogleAccount(null);
        student.setRishiGoogleCalendarId(defaultGoogleCalendarId);
        studentRepository.save(student);
    }

    public List<RishiTaskDto> getTasks(Student student) {
        return taskRepository.findByStudentOrderByUpdatedAtDesc(student).stream()
                .map(this::toTaskDto)
                .toList();
    }

    @Transactional
    public void markTaskDone(Student student, Long taskId) {
        RishiPracticeTask task = taskRepository.findByIdAndStudent(taskId, student)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        task.setStatus(STATUS_DONE);
        taskRepository.save(task);
    }

    @Transactional
    public RishiScheduleResponse scheduleNextBlocks(Student student, RishiScheduleRequest request) {
        String token = ensureGoogleAccessToken(student);
        int count = request == null || request.getCount() == null ? 2 : Math.max(1, Math.min(8, request.getCount()));
        int blockMinutes = request == null || request.getBlockMinutes() == null
                ? 50
                : Math.max(20, Math.min(180, request.getBlockMinutes()));
        ZoneId zone = resolveZone(request == null ? null : request.getTimeZone());
        LocalDateTime start = parseDateTime(request == null ? null : request.getStartAt());
        if (start == null) {
            start = LocalDateTime.now(zone).plusMinutes(15).withSecond(0).withNano(0);
        }

        List<RishiPracticeTask> pending = loadOrCreatePendingTasks(student, count, blockMinutes);

        List<RishiScheduleItemDto> items = new ArrayList<>();
        String calendarId = isBlank(student.getRishiGoogleCalendarId()) ? defaultGoogleCalendarId : student.getRishiGoogleCalendarId();
        LocalDateTime cursor = start;

        for (RishiPracticeTask task : pending) {
            int minutes = task.getSuggestedMinutes() == null
                    ? blockMinutes
                    : Math.max(20, Math.min(180, task.getSuggestedMinutes()));
            LocalDateTime end = cursor.plusMinutes(minutes);

            Map<String, Object> event = createGoogleCalendarEvent(token, calendarId, task, cursor, end, zone.getId());
            String eventId = asString(event.get("id"));
            String eventLink = asString(event.get("htmlLink"));

            task.setStatus(STATUS_SCHEDULED);
            task.setPlannedStartAt(cursor);
            task.setPlannedEndAt(end);
            task.setCalendarEventId(eventId);
            task.setCalendarEventLink(eventLink);
            taskRepository.save(task);

            items.add(RishiScheduleItemDto.builder()
                    .taskId(task.getId())
                    .taskTitle(task.getTitle())
                    .startAt(formatDateTime(cursor))
                    .endAt(formatDateTime(end))
                    .eventId(eventId)
                    .eventLink(eventLink)
                    .build());

            cursor = end.plusMinutes(10);
        }

        return RishiScheduleResponse.builder()
                .scheduledCount(items.size())
                .items(items)
                .build();
    }

    @Transactional
    public RishiScheduleResponse autoRescheduleMissedBlocks(Student student, RishiScheduleRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<RishiPracticeTask> tasks = taskRepository.findByStudentOrderByUpdatedAtDesc(student);
        for (RishiPracticeTask task : tasks) {
            boolean scheduled = STATUS_SCHEDULED.equalsIgnoreCase(task.getStatus());
            boolean endedInPast = task.getPlannedEndAt() != null && task.getPlannedEndAt().isBefore(now);
            if (scheduled && endedInPast) {
                task.setStatus(STATUS_MISSED);
                taskRepository.save(task);
            }
        }
        return scheduleNextBlocks(student, request);
    }

    public RishiCoachingSummaryResponse getCoachingSummary(Student student, int windowDays) {
        int safeWindowDays = Math.max(3, Math.min(30, windowDays));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<RishiPracticeTask> tasks = taskRepository.findByStudentOrderByUpdatedAtDesc(student);
        long pendingTasks = tasks.stream().filter(task -> !STATUS_DONE.equalsIgnoreCase(task.getStatus())).count();
        long missedTasks = tasks.stream().filter(task -> STATUS_MISSED.equalsIgnoreCase(task.getStatus())).count();

        String recommendedFocus = tasks.stream()
                .filter(task -> !STATUS_DONE.equalsIgnoreCase(task.getStatus()))
                .sorted(Comparator.comparingInt((RishiPracticeTask task) -> task.getPriority() == null ? 1 : task.getPriority()).reversed()
                        .thenComparing(RishiPracticeTask::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(task -> {
                    String topic = nullToEmpty(task.getTopic()).trim();
                    return topic.isBlank() ? task.getTitle() : topic;
                })
                .findFirst()
                .orElse("Algorithm practice");

        RishiFocusMetricsDto focus = getFocusMetrics(student, safeWindowDays);
        int baseMinutes = focus.getAdherencePct() >= 80 ? 75 : focus.getAdherencePct() >= 50 ? 90 : 110;
        int missedAdjustment = (int) Math.min(40, missedTasks * 10);
        int recommendedMinutesToday = Math.max(45, Math.min(180, baseMinutes + missedAdjustment));

        List<String> nextActions = new ArrayList<>();
        nextActions.add("Start with a " + Math.min(60, recommendedMinutesToday) + "-minute block on " + recommendedFocus + ".");
        if (missedTasks > 0) {
            nextActions.add("Reschedule " + missedTasks + " missed block(s) before adding new tasks.");
        } else {
            nextActions.add("Schedule the next 2 focus blocks in Google Calendar.");
        }
        nextActions.add("Close with a 15-minute review of mistakes from failed compiler attempts.");

        String summary = "Focus adherence is " + String.format("%.1f%%", focus.getAdherencePct())
                + " over the last " + safeWindowDays + " day(s). "
                + "Pending tasks: " + pendingTasks + ", missed blocks: " + missedTasks + ". "
                + "Primary focus: " + recommendedFocus + ".";

        return RishiCoachingSummaryResponse.builder()
                .windowDays(safeWindowDays)
                .pendingTasks((int) pendingTasks)
                .missedTasks((int) missedTasks)
                .plannedMinutes(focus.getPlannedMinutes())
                .actualMinutes(focus.getActualMinutes())
                .adherencePct(focus.getAdherencePct())
                .recommendedMinutesToday(recommendedMinutesToday)
                .recommendedFocus(recommendedFocus)
                .summary(summary)
                .generatedAt(formatDateTime(now))
                .nextActions(nextActions)
                .build();
    }

    public boolean isGoogleCalendarConnected(Student student) {
        return !isBlank(student.getRishiGoogleAccessTokenEncrypted());
    }

    public boolean isGithubConnected(Student student) {
        return !isBlank(student.getGithubUsername()) && !isBlank(student.getGithubAccessToken());
    }

    public boolean isLeetCodeConnected(Student student) {
        return !isBlank(student.getLeetcodeUsername());
    }

    public boolean isCodeforcesConnected(Student student) {
        return !isBlank(student.getCodeforcesHandle());
    }

    public boolean isTogglConnected(Student student) {
        return !isBlank(student.getRishiTogglApiTokenEncrypted());
    }

    public Optional<RishiCodeforcesAnalyticsDto> getLatestCodeforcesAnalytics(Student student) {
        return codeforcesAnalyticsSnapshotRepository.findTopByStudentOrderByCreatedAtDesc(student).map(this::toCodeforcesAnalyticsDto);
    }

    public Optional<RishiTogglFocusDto> getLatestTogglFocus(Student student) {
        return togglFocusSnapshotRepository.findTopByStudentOrderByCreatedAtDesc(student).map(this::toTogglFocusDto);
    }

    public Optional<RishiGithubAnalyticsDto> getLatestGithubAnalytics(Student student) {
        return githubAnalyticsSnapshotRepository.findTopByStudentOrderByCreatedAtDesc(student).map(this::toGithubAnalyticsDto);
    }

    public Optional<RishiLeetCodeAnalyticsDto> getLatestLeetCodeAnalytics(Student student) {
        return leetCodeAnalyticsSnapshotRepository.findTopByStudentOrderByCreatedAtDesc(student).map(this::toLeetCodeAnalyticsDto);
    }

    public RishiFocusMetricsDto getFocusMetrics(Student student, int windowDays) {
        int safeWindowDays = Math.max(3, Math.min(30, windowDays));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime windowStart = now.minusDays(safeWindowDays);

        List<RishiPracticeTask> plannedTasks = taskRepository.findByStudentAndPlannedStartAtBetween(student, windowStart, now);
        List<RishiCodingSession> codingSessions = codingSessionRepository.findByStudentAndStartedAtBetween(student, windowStart, now);

        long plannedMinutes = 0L;
        for (RishiPracticeTask task : plannedTasks) {
            if (task.getPlannedStartAt() != null && task.getPlannedEndAt() != null
                    && task.getPlannedEndAt().isAfter(task.getPlannedStartAt())) {
                long durationMs = ChronoUnit.MILLIS.between(task.getPlannedStartAt(), task.getPlannedEndAt());
                plannedMinutes += Math.max(0L, Math.round(durationMs / 60000.0));
            } else if (task.getSuggestedMinutes() != null) {
                plannedMinutes += Math.max(0, task.getSuggestedMinutes());
            }
        }

        long actualMinutes = 0L;
        for (RishiCodingSession session : codingSessions) {
            long activeDurationMs = session.getActiveDurationMs() == null ? 0L : session.getActiveDurationMs();
            long totalDurationMs = session.getTotalDurationMs() == null ? 0L : session.getTotalDurationMs();
            long usedDurationMs = activeDurationMs > 0 ? activeDurationMs : totalDurationMs;
            actualMinutes += Math.max(0L, Math.round(usedDurationMs / 60000.0));
        }
        int actualSessions = codingSessions.size();
        String dataSource = "RISHI_TELEMETRY";

        Optional<RishiTogglFocusDto> togglFocus = getLatestTogglFocus(student)
                .filter(snapshot -> snapshot.getWindowDays() == safeWindowDays);
        if (togglFocus.isPresent()) {
            RishiTogglFocusDto togglSnapshot = togglFocus.get();
            actualMinutes = Math.max(actualMinutes, Math.max(0L, togglSnapshot.getTrackedMinutes()));
            actualSessions = Math.max(actualSessions, Math.max(0, togglSnapshot.getEntryCount()));
            dataSource = "RISHI_TELEMETRY+TOGGL_MAX";
        }

        double adherencePct;
        if (plannedMinutes <= 0) {
            adherencePct = actualMinutes > 0 ? 100.0 : 0.0;
        } else {
            adherencePct = round2((actualMinutes * 100.0) / plannedMinutes);
        }

        return RishiFocusMetricsDto.builder()
                .windowDays(safeWindowDays)
                .plannedMinutes(plannedMinutes)
                .actualMinutes(actualMinutes)
                .plannedSessions(plannedTasks.size())
                .actualSessions(actualSessions)
                .adherencePct(adherencePct)
                .dataSource(dataSource)
                .build();
    }

    @Transactional
    public void setTogglToken(Student student, String apiToken) {
        String token = nullToEmpty(apiToken).trim();
        if (isBlank(token)) {
            throw new IllegalArgumentException("Toggl API token is required.");
        }

        validateTogglToken(token);
        student.setRishiTogglApiTokenEncrypted(tokenCryptoService.encrypt(token));
        studentRepository.save(student);
    }

    @Transactional
    public void disconnectToggl(Student student) {
        student.setRishiTogglApiTokenEncrypted(null);
        studentRepository.save(student);
    }

    @Transactional
    public RishiTogglFocusDto syncTogglFocus(Student student, Integer requestedWindowDays) {
        if (!isTogglConnected(student)) {
            throw new IllegalStateException("Toggl is not connected.");
        }

        int windowDays = requestedWindowDays == null ? DEFAULT_TOGGL_WINDOW_DAYS
                : Math.max(MIN_TOGGL_WINDOW_DAYS, Math.min(MAX_TOGGL_WINDOW_DAYS, requestedWindowDays));
        String apiToken = tokenCryptoService.decrypt(student.getRishiTogglApiTokenEncrypted());
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime startUtc = nowUtc.minusDays(windowDays);

        List<Map<String, Object>> entries = fetchTogglTimeEntries(apiToken, startUtc, nowUtc);
        long trackedSeconds = 0L;
        int entryCount = 0;
        for (Map<String, Object> entry : entries) {
            long durationSeconds = parseLong(entry.get("duration"), 0L);
            if (durationSeconds > 0) {
                trackedSeconds += durationSeconds;
                entryCount++;
                continue;
            }

            // Running timer entries can expose negative duration; derive from start/stop when available.
            LocalDateTime start = parseDateTime(asString(entry.get("start")));
            LocalDateTime stop = parseDateTime(asString(entry.get("stop")));
            if (start != null && stop != null && stop.isAfter(start)) {
                trackedSeconds += ChronoUnit.SECONDS.between(start, stop);
                entryCount++;
            }
        }

        long trackedMinutes = Math.max(0L, Math.round(trackedSeconds / 60.0));
        RishiTogglFocusSnapshot snapshot = RishiTogglFocusSnapshot.builder()
                .student(student)
                .windowDays(windowDays)
                .trackedMinutes(trackedMinutes)
                .entryCount(entryCount)
                .build();
        RishiTogglFocusSnapshot saved = togglFocusSnapshotRepository.save(snapshot);
        return toTogglFocusDto(saved);
    }

    @Transactional
    public RishiGithubAnalyticsDto syncGithubAnalytics(Student student, Integer requestedWindowDays) {
        String githubUsername = nullToEmpty(student.getGithubUsername()).trim();
        String githubToken = nullToEmpty(student.getGithubAccessToken()).trim();
        if (isBlank(githubUsername)) {
            throw new IllegalStateException("GitHub username is not linked.");
        }
        if (isBlank(githubToken)) {
            throw new IllegalStateException("GitHub token is not linked.");
        }

        int windowDays = requestedWindowDays == null ? DEFAULT_GITHUB_WINDOW_DAYS
                : Math.max(MIN_GITHUB_WINDOW_DAYS, Math.min(MAX_GITHUB_WINDOW_DAYS, requestedWindowDays));
        Map<String, Object> analytics = fetchGithubAnalytics(githubUsername, githubToken, windowDays);

        RishiGithubAnalyticsSnapshot snapshot = RishiGithubAnalyticsSnapshot.builder()
                .student(student)
                .githubUsername(githubUsername)
                .windowDays(windowDays)
                .commitCount(parseInt(analytics.get("commitCount"), 0))
                .pullRequestCount(parseInt(analytics.get("pullRequestCount"), 0))
                .reviewCount(parseInt(analytics.get("reviewCount"), 0))
                .issueCount(parseInt(analytics.get("issueCount"), 0))
                .activeRepoCount(parseInt(analytics.get("activeRepoCount"), 0))
                .totalStars(parseInt(analytics.get("totalStars"), 0))
                .topLanguages(asString(analytics.get("topLanguages")))
                .build();

        RishiGithubAnalyticsSnapshot saved = githubAnalyticsSnapshotRepository.save(snapshot);
        return toGithubAnalyticsDto(saved);
    }

    @Transactional
    public RishiLeetCodeAnalyticsDto syncLeetCodeAnalytics(Student student, Integer requestedWindowDays) {
        String leetcodeUsername = nullToEmpty(student.getLeetcodeUsername()).trim();
        if (isBlank(leetcodeUsername)) {
            throw new IllegalStateException("LeetCode username is not linked.");
        }

        int windowDays = requestedWindowDays == null ? DEFAULT_LEETCODE_WINDOW_DAYS
                : Math.max(MIN_LEETCODE_WINDOW_DAYS, Math.min(MAX_LEETCODE_WINDOW_DAYS, requestedWindowDays));
        Map<String, Object> stats = leetCodeService.fetchFullStats(leetcodeUsername);
        if (stats.isEmpty()) {
            throw new IllegalStateException("Unable to fetch LeetCode analytics. Confirm username and retry.");
        }

        Map<String, Object> contestStats = fetchLeetCodeContestStats(leetcodeUsername);
        List<Map<String, Object>> recentAccepted = safeMapList(stats.get("recentAcSubmissions"));
        List<Map<String, Object>> algorithmMastery = safeMapList(stats.get("algorithmMastery"));

        int solvedLast7d = countAcceptedSubmissionsInWindow(recentAccepted, 7, 0);
        int solvedPrev7d = countAcceptedSubmissionsInWindow(recentAccepted, 14, 7);
        double solveTrendPct = computeSolveTrendPct(solvedLast7d, solvedPrev7d);

        RishiLeetCodeAnalyticsSnapshot snapshot = RishiLeetCodeAnalyticsSnapshot.builder()
                .student(student)
                .leetcodeUsername(leetcodeUsername)
                .windowDays(windowDays)
                .totalSolved(parseInt(stats.get("totalSolved"), 0))
                .easySolved(parseInt(stats.get("easySolved"), 0))
                .mediumSolved(parseInt(stats.get("mediumSolved"), 0))
                .hardSolved(parseInt(stats.get("hardSolved"), 0))
                .ranking(parseInt(stats.get("ranking"), 0))
                .reputation(parseInt(stats.get("reputation"), 0))
                .contestRating(parseDouble(contestStats.get("contestRating"), 0.0))
                .contestAttendedCount(parseInt(contestStats.get("contestAttendedCount"), 0))
                .solvedLast7d(solvedLast7d)
                .solvedPrev7d(solvedPrev7d)
                .solveTrendPct(solveTrendPct)
                .weakTopics(summarizeLeetCodeTopics(algorithmMastery, true))
                .strongTopics(summarizeLeetCodeTopics(algorithmMastery, false))
                .build();

        RishiLeetCodeAnalyticsSnapshot saved = leetCodeAnalyticsSnapshotRepository.save(snapshot);
        return toLeetCodeAnalyticsDto(saved);
    }

    @Transactional
    public String setCodeforcesHandle(Student student, String requestedHandle) {
        String handle = nullToEmpty(requestedHandle).trim();
        if (isBlank(handle)) {
            student.setCodeforcesHandle(null);
            studentRepository.save(student);
            return "";
        }

        if (!isValidCodeforcesHandle(handle)) {
            throw new IllegalArgumentException("Invalid Codeforces handle.");
        }

        // Validate handle by calling user.info once.
        fetchCodeforcesUserInfo(handle);
        student.setCodeforcesHandle(handle);
        studentRepository.save(student);
        return handle;
    }

    @Transactional
    public RishiCodeforcesAnalyticsDto syncCodeforcesAnalytics(Student student, Integer requestedWindowDays) {
        String handle = nullToEmpty(student.getCodeforcesHandle()).trim();
        if (isBlank(handle)) {
            throw new IllegalStateException("Codeforces handle is not linked.");
        }

        int windowDays = requestedWindowDays == null ? DEFAULT_CODEFORCES_WINDOW_DAYS
                : Math.max(MIN_CODEFORCES_WINDOW_DAYS, Math.min(MAX_CODEFORCES_WINDOW_DAYS, requestedWindowDays));

        Map<String, Object> userInfo = fetchCodeforcesUserInfo(handle);
        List<Map<String, Object>> ratingHistory = fetchCodeforcesRatingHistory(handle);
        List<Map<String, Object>> submissions = fetchCodeforcesSubmissions(handle, MAX_CODEFORCES_SUBMISSIONS_FETCH);

        Map<String, Integer> acceptedByTag = new HashMap<>();
        Map<String, Integer> attemptsByTag = new HashMap<>();
        Map<String, Integer> acceptedWindowByTag = new HashMap<>();
        Set<String> acceptedProblemKeys = new HashSet<>();
        Set<String> acceptedCurrentWindowKeys = new HashSet<>();
        Set<String> acceptedPreviousWindowKeys = new HashSet<>();

        long nowEpochSeconds = Instant.now().getEpochSecond();
        long currentStart = nowEpochSeconds - (long) windowDays * 24L * 60L * 60L;
        long previousStart = nowEpochSeconds - (long) (windowDays * 2) * 24L * 60L * 60L;

        for (Map<String, Object> submission : submissions) {
            String verdict = asString(submission.get("verdict"));
            long creationTime = parseLong(submission.get("creationTimeSeconds"), 0L);
            Map<String, Object> problem = safeMap(submission.get("problem"));
            String problemKey = buildCodeforcesProblemKey(problem);
            List<String> tags = extractCodeforcesTags(problem.get("tags"));

            boolean inCurrentWindow = creationTime >= currentStart;
            if (inCurrentWindow) {
                for (String tag : tags) {
                    attemptsByTag.merge(tag, 1, Integer::sum);
                }
            }

            if (!"OK".equalsIgnoreCase(verdict)) {
                continue;
            }

            if (!problemKey.isEmpty()) {
                acceptedProblemKeys.add(problemKey);
                if (inCurrentWindow) {
                    acceptedCurrentWindowKeys.add(problemKey);
                } else if (creationTime >= previousStart) {
                    acceptedPreviousWindowKeys.add(problemKey);
                }
            }

            for (String tag : tags) {
                acceptedByTag.merge(tag, 1, Integer::sum);
                if (inCurrentWindow) {
                    acceptedWindowByTag.merge(tag, 1, Integer::sum);
                    attemptsByTag.merge(tag, 1, Integer::sum);
                }
            }
        }

        int solvedCurrentWindow = acceptedCurrentWindowKeys.size();
        int solvedPreviousWindow = acceptedPreviousWindowKeys.size();

        RishiCodeforcesAnalyticsSnapshot snapshot = RishiCodeforcesAnalyticsSnapshot.builder()
                .student(student)
                .codeforcesHandle(handle)
                .windowDays(windowDays)
                .currentRating(parseInt(userInfo.get("rating"), 0))
                .maxRating(parseInt(userInfo.get("maxRating"), 0))
                .rank(asString(userInfo.get("rank")))
                .maxRank(asString(userInfo.get("maxRank")))
                .contestCount(ratingHistory.size())
                .solvedTotal(acceptedProblemKeys.size())
                .solvedCurrentWindow(solvedCurrentWindow)
                .solvedPreviousWindow(solvedPreviousWindow)
                .solveTrendPct(computeSolveTrendPct(solvedCurrentWindow, solvedPreviousWindow))
                .strongTags(summarizeTopCodeforcesTags(acceptedByTag, 4))
                .weakTags(summarizeWeakCodeforcesTags(attemptsByTag, acceptedWindowByTag, 4))
                .build();

        RishiCodeforcesAnalyticsSnapshot saved = codeforcesAnalyticsSnapshotRepository.save(snapshot);
        return toCodeforcesAnalyticsDto(saved);
    }

    private Map<String, Object> fetchGithubAnalytics(String githubUsername, String githubToken, int windowDays) {
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        OffsetDateTime from = to.minusDays(windowDays);

        String query = """
                query($login: String!, $from: DateTime!, $to: DateTime!) {
                  user(login: $login) {
                    login
                    repositories(ownerAffiliations: OWNER, first: 100, isFork: false, orderBy: {field: UPDATED_AT, direction: DESC}) {
                      totalCount
                      nodes {
                        stargazerCount
                        primaryLanguage {
                          name
                        }
                      }
                    }
                    contributionsCollection(from: $from, to: $to) {
                      totalCommitContributions
                      totalPullRequestContributions
                      totalPullRequestReviewContributions
                      totalIssueContributions
                    }
                  }
                  rateLimit {
                    remaining
                    resetAt
                  }
                }
                """;

        Map<String, Object> variables = Map.of(
                "login", githubUsername,
                "from", from.toString(),
                "to", to.toString());
        Map<String, Object> body = Map.of("query", query, "variables", variables);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("User-Agent", "StudentSkillTracker/1.0");
        headers.setBearerAuth(githubToken);

        Map<String, Object> response = exchangeForMap(
                "https://api.github.com/graphql",
                HttpMethod.POST,
                new HttpEntity<>(body, headers));
        validateGithubGraphQlResponse(response);

        Map<String, Object> data = safeMap(response.get("data"));
        Map<String, Object> user = safeMap(data.get("user"));
        if (user.isEmpty()) {
            throw new IllegalStateException("GitHub user not found or token lacks access.");
        }

        Map<String, Object> repos = safeMap(user.get("repositories"));
        List<Map<String, Object>> repoNodes = safeMapList(repos.get("nodes"));
        Map<String, Object> contributions = safeMap(user.get("contributionsCollection"));

        int totalStars = 0;
        Map<String, Integer> languageCounts = new HashMap<>();
        for (Map<String, Object> repoNode : repoNodes) {
            totalStars += parseInt(repoNode.get("stargazerCount"), 0);
            Map<String, Object> language = safeMap(repoNode.get("primaryLanguage"));
            String languageName = asString(language.get("name")).trim();
            if (!languageName.isEmpty()) {
                languageCounts.merge(languageName, 1, Integer::sum);
            }
        }

        String topLanguages = languageCounts.entrySet().stream()
                .sorted((a, b) -> {
                    int byCount = Integer.compare(b.getValue(), a.getValue());
                    return byCount != 0 ? byCount : a.getKey().compareToIgnoreCase(b.getKey());
                })
                .limit(5)
                .map(Map.Entry::getKey)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return Map.of(
                "commitCount", parseInt(contributions.get("totalCommitContributions"), 0),
                "pullRequestCount", parseInt(contributions.get("totalPullRequestContributions"), 0),
                "reviewCount", parseInt(contributions.get("totalPullRequestReviewContributions"), 0),
                "issueCount", parseInt(contributions.get("totalIssueContributions"), 0),
                "activeRepoCount", parseInt(repos.get("totalCount"), repoNodes.size()),
                "totalStars", totalStars,
                "topLanguages", topLanguages);
    }

    private Map<String, Object> fetchLeetCodeContestStats(String leetcodeUsername) {
        String query = """
                query userContestMetrics($username: String!) {
                  userContestRanking(username: $username) {
                    rating
                    attendedContestsCount
                  }
                  userContestRankingHistory(username: $username) {
                    attended
                  }
                }
                """;

        Map<String, Object> body = Map.of(
                "query", query,
                "variables", Map.of("username", leetcodeUsername));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Origin", "https://leetcode.com");
        headers.set("Referer", "https://leetcode.com/contest/");
        headers.set("User-Agent", "StudentSkillTracker/1.0");

        try {
            Map<String, Object> response = exchangeForMap(
                    "https://leetcode.com/graphql/",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers));
            Map<String, Object> data = safeMap(response.get("data"));
            Map<String, Object> ranking = safeMap(data.get("userContestRanking"));
            List<Map<String, Object>> history = safeMapList(data.get("userContestRankingHistory"));

            double rating = parseDouble(ranking.get("rating"), 0.0);
            int attendedCount = parseInt(ranking.get("attendedContestsCount"), -1);
            if (attendedCount < 0) {
                attendedCount = (int) history.stream()
                        .filter(item -> Boolean.TRUE.equals(item.get("attended")))
                        .count();
            }

            return Map.of(
                    "contestRating", round2(rating),
                    "contestAttendedCount", Math.max(0, attendedCount));
        } catch (IllegalStateException ex) {
            logger.debug("LeetCode contest stats unavailable for {}: {}", leetcodeUsername, ex.getMessage());
            return Map.of(
                    "contestRating", 0.0,
                    "contestAttendedCount", 0);
        }
    }

    private Map<String, Object> fetchCodeforcesUserInfo(String handle) {
        String url = UriComponentsBuilder.fromHttpUrl("https://codeforces.com/api/user.info")
                .queryParam("handles", handle)
                .toUriString();
        List<Map<String, Object>> result = fetchCodeforcesApiResultList(url, "user.info");
        if (result.isEmpty()) {
            throw new IllegalStateException("Codeforces handle not found.");
        }
        return result.get(0);
    }

    private List<Map<String, Object>> fetchCodeforcesRatingHistory(String handle) {
        String url = UriComponentsBuilder.fromHttpUrl("https://codeforces.com/api/user.rating")
                .queryParam("handle", handle)
                .toUriString();
        return fetchCodeforcesApiResultList(url, "user.rating");
    }

    private List<Map<String, Object>> fetchCodeforcesSubmissions(String handle, int count) {
        String url = UriComponentsBuilder.fromHttpUrl("https://codeforces.com/api/user.status")
                .queryParam("handle", handle)
                .queryParam("from", 1)
                .queryParam("count", Math.max(50, count))
                .toUriString();
        return fetchCodeforcesApiResultList(url, "user.status");
    }

    private List<Map<String, Object>> fetchCodeforcesApiResultList(String url, String endpointName) {
        Map<String, Object> response = exchangeForMap(url, HttpMethod.GET, new HttpEntity<>(null, new HttpHeaders()));
        String status = asString(response.get("status"));
        if (!"OK".equalsIgnoreCase(status)) {
            String comment = asString(response.get("comment"));
            String errorMessage = isBlank(comment) ? "Codeforces API request failed." : comment;
            throw new IllegalStateException("Codeforces " + endpointName + " error: " + errorMessage);
        }
        return safeMapList(response.get("result"));
    }

    private void validateTogglToken(String apiToken) {
        HttpHeaders headers = togglHeaders(apiToken);
        Map<String, Object> response = exchangeForMap(
                "https://api.track.toggl.com/api/v9/me",
                HttpMethod.GET,
                new HttpEntity<>(null, headers));
        if (response.isEmpty() || isBlank(asString(response.get("id")))) {
            throw new IllegalStateException("Invalid Toggl API token.");
        }
    }

    private List<Map<String, Object>> fetchTogglTimeEntries(String apiToken, LocalDateTime startUtc, LocalDateTime endUtc) {
        String startDate = startUtc.atOffset(ZoneOffset.UTC).toString();
        String endDate = endUtc.atOffset(ZoneOffset.UTC).toString();
        String url = UriComponentsBuilder.fromHttpUrl("https://api.track.toggl.com/api/v9/me/time_entries")
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate)
                .toUriString();

        HttpHeaders headers = togglHeaders(apiToken);
        Map<String, Object> wrapper = Map.of("result", exchangeForList(url, headers));
        return safeMapList(wrapper.get("result"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> exchangeForList(String url, HttpHeaders headers) {
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, headers),
                    List.class);
            List<?> body = response.getBody();
            if (body == null) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : body) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        } catch (HttpStatusCodeException ex) {
            String detail = extractConnectorErrorDetail(ex.getResponseBodyAsString());
            String message = isBlank(detail) ? ("HTTP " + ex.getStatusCode().value()) : detail;
            throw new IllegalStateException("Toggl request failed: " + message);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Toggl request failed. Check token and retry.");
        }
    }

    private HttpHeaders togglHeaders(String apiToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(apiToken, "api_token");
        return headers;
    }

    private boolean isValidCodeforcesHandle(String handle) {
        if (isBlank(handle)) {
            return false;
        }
        return handle.matches("^[A-Za-z0-9_.-]{3,40}$");
    }

    private String buildCodeforcesProblemKey(Map<String, Object> problem) {
        if (problem == null || problem.isEmpty()) {
            return "";
        }
        String contestId = asString(problem.get("contestId"));
        String index = asString(problem.get("index"));
        if (!isBlank(contestId) && !isBlank(index)) {
            return contestId + "-" + index;
        }
        String problemsetName = asString(problem.get("problemsetName"));
        String name = asString(problem.get("name"));
        if (!isBlank(problemsetName) && !isBlank(name)) {
            return problemsetName + "-" + name;
        }
        return name;
    }

    private List<String> extractCodeforcesTags(Object tagsObj) {
        if (!(tagsObj instanceof List<?> tags)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object tagObj : tags) {
            String tag = asString(tagObj).trim();
            if (!tag.isEmpty()) {
                values.add(tag);
            }
        }
        return values;
    }

    private String summarizeTopCodeforcesTags(Map<String, Integer> tagCounts, int limit) {
        if (tagCounts == null || tagCounts.isEmpty()) {
            return "";
        }
        return tagCounts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted((a, b) -> {
                    int byValue = Integer.compare(b.getValue(), a.getValue());
                    return byValue != 0 ? byValue : a.getKey().compareToIgnoreCase(b.getKey());
                })
                .limit(Math.max(1, limit))
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    private String summarizeWeakCodeforcesTags(
            Map<String, Integer> attemptsByTag,
            Map<String, Integer> acceptedByTag,
            int limit) {
        if (attemptsByTag == null || attemptsByTag.isEmpty()) {
            return "";
        }
        return attemptsByTag.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() >= 2)
                .sorted((a, b) -> {
                    double rateA = ((double) acceptedByTag.getOrDefault(a.getKey(), 0)) / a.getValue();
                    double rateB = ((double) acceptedByTag.getOrDefault(b.getKey(), 0)) / b.getValue();
                    int byRate = Double.compare(rateA, rateB);
                    if (byRate != 0) {
                        return byRate;
                    }
                    int byAttempts = Integer.compare(b.getValue(), a.getValue());
                    return byAttempts != 0 ? byAttempts : a.getKey().compareToIgnoreCase(b.getKey());
                })
                .limit(Math.max(1, limit))
                .map(entry -> {
                    int attempts = entry.getValue();
                    int accepted = acceptedByTag.getOrDefault(entry.getKey(), 0);
                    int successPct = attempts <= 0 ? 0 : (int) Math.round((accepted * 100.0) / attempts);
                    return entry.getKey() + " (" + successPct + "%)";
                })
                .collect(Collectors.joining(", "));
    }

    private void validateGithubGraphQlResponse(Map<String, Object> response) {
        Object errorsObj = response.get("errors");
        if (!(errorsObj instanceof List<?> errors) || errors.isEmpty()) {
            return;
        }
        Object first = errors.get(0);
        if (first instanceof Map<?, ?> errorMap) {
            String message = asString(errorMap.get("message"));
            throw new IllegalStateException("GitHub GraphQL error: " + (isBlank(message) ? "Unknown error" : message));
        }
        throw new IllegalStateException("GitHub GraphQL error.");
    }

    private List<RishiPracticeTask> loadOrCreatePendingTasks(Student student, int count, int blockMinutes) {
        List<RishiPracticeTask> pending = taskRepository.findByStudentAndStatusInOrderByPriorityDescUpdatedAtAsc(
                student,
                List.of(STATUS_MISSED, STATUS_TODO, STATUS_IN_PROGRESS),
                PageRequest.of(0, count));
        if (!pending.isEmpty()) {
            return pending;
        }

        List<RishiPracticeTask> generated = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RishiPracticeTask task = RishiPracticeTask.builder()
                    .student(student)
                    .sourceType(SOURCE_TYPE_AUTO)
                    .sourceExternalId("auto-calendar-" + UUID.randomUUID())
                    .title("Focused coding session " + (i + 1))
                    .details("Auto-generated by Rishi scheduler. Solve one medium problem and review mistakes.")
                    .topic("Algorithm Practice")
                    .priority(1)
                    .status(STATUS_TODO)
                    .suggestedMinutes(blockMinutes)
                    .build();
            generated.add(taskRepository.save(task));
        }
        return generated;
    }

    private void exchangeGoogleAuthorizationCode(Student student, String code, String redirectUri) {
        String tokenEndpoint = "https://oauth2.googleapis.com/token";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        Map<String, Object> tokenPayload = postForm(tokenEndpoint, form);
        applyGoogleTokens(student, tokenPayload);
    }

    private String ensureGoogleAccessToken(Student student) {
        if (isBlank(student.getRishiGoogleAccessTokenEncrypted())) {
            throw new IllegalStateException("Google Calendar is not connected.");
        }
        LocalDateTime expiresAt = student.getRishiGoogleTokenExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1))) {
            refreshGoogleToken(student);
        }
        return tokenCryptoService.decrypt(student.getRishiGoogleAccessTokenEncrypted());
    }

    private void refreshGoogleToken(Student student) {
        String refreshTokenEncrypted = student.getRishiGoogleRefreshTokenEncrypted();
        if (isBlank(refreshTokenEncrypted)) {
            throw new IllegalStateException("Google refresh token missing. Reconnect Google Calendar.");
        }

        String tokenEndpoint = "https://oauth2.googleapis.com/token";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", tokenCryptoService.decrypt(refreshTokenEncrypted));

        Map<String, Object> tokenPayload = postForm(tokenEndpoint, form);
        applyGoogleTokens(student, tokenPayload);
        studentRepository.save(student);
    }

    private void applyGoogleTokens(Student student, Map<String, Object> tokenPayload) {
        String accessToken = asString(tokenPayload.get("access_token"));
        if (isBlank(accessToken)) {
            throw new IllegalStateException("Google token exchange failed: missing access token.");
        }
        String refreshToken = asString(tokenPayload.get("refresh_token"));
        int expiresIn = parseInt(tokenPayload.get("expires_in"), 3600);

        student.setRishiGoogleAccessTokenEncrypted(tokenCryptoService.encrypt(accessToken));
        if (!isBlank(refreshToken)) {
            student.setRishiGoogleRefreshTokenEncrypted(tokenCryptoService.encrypt(refreshToken));
        }
        if (isBlank(student.getRishiGoogleCalendarId())) {
            student.setRishiGoogleCalendarId(defaultGoogleCalendarId);
        }
        student.setRishiGoogleTokenExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(Math.max(300, expiresIn - 30L)));
    }

    private Map<String, Object> createGoogleCalendarEvent(
            String accessToken,
            String calendarId,
            RishiPracticeTask task,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String timeZone) {
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ZonedDateTime startZoned = startAt.atZone(ZoneId.of(timeZone));
        ZonedDateTime endZoned = endAt.atZone(ZoneId.of(timeZone));

        Map<String, Object> body = Map.of(
                "summary", "[Rishi] " + task.getTitle(),
                "description", nullToEmpty(task.getDetails()),
                "start", Map.of("dateTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(startZoned), "timeZone", timeZone),
                "end", Map.of("dateTime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(endZoned), "timeZone", timeZone),
                "reminders", Map.of(
                        "useDefault", false,
                        "overrides", List.of(
                                Map.of("method", "popup", "minutes", 10),
                                Map.of("method", "popup", "minutes", 2))));

        String safeCalendarId = isBlank(calendarId) ? "primary" : calendarId.trim();
        String url = "https://www.googleapis.com/calendar/v3/calendars/" + safeCalendarId + "/events";
        return exchangeForMap(url, HttpMethod.POST, new HttpEntity<>(body, headers));
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return exchangeForMap(url, HttpMethod.POST, new HttpEntity<>(form, headers));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeForMap(String url, HttpMethod method, HttpEntity<?> entity) {
        for (int attempt = 1; attempt <= INTEGRATION_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, method, entity, Map.class);
                Map<String, Object> body = response.getBody();
                return body == null ? Map.of() : (Map<String, Object>) body;
            } catch (HttpStatusCodeException ex) {
                int statusCode = ex.getStatusCode().value();
                String responseBody = nullToEmpty(ex.getResponseBodyAsString());
                String detail = extractConnectorErrorDetail(responseBody);
                String safeDetail = isBlank(detail) ? ("HTTP " + statusCode) : detail;
                boolean retryable = isRetryableHttpStatus(statusCode) && attempt < INTEGRATION_RETRY_MAX_ATTEMPTS;
                logger.error("Integration request failed: {} {} -> status={} detail={} attempt={}/{}",
                        method, url, statusCode, safeDetail, attempt, INTEGRATION_RETRY_MAX_ATTEMPTS);
                if (retryable) {
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw new IllegalStateException("Integration request failed: " + safeDetail);
            } catch (RestClientException ex) {
                boolean retryable = attempt < INTEGRATION_RETRY_MAX_ATTEMPTS;
                logger.error("Integration request failed: {} {} -> {} attempt={}/{}",
                        method, url, ex.getMessage(), attempt, INTEGRATION_RETRY_MAX_ATTEMPTS);
                if (retryable) {
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw new IllegalStateException("Integration request failed. Check connector configuration and retry.");
            }
        }
        throw new IllegalStateException("Integration request failed. Retry limit reached.");
    }

    private boolean isRetryableHttpStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt) {
        long delayMs = Math.min(2000L, INTEGRATION_RETRY_BASE_DELAY_MS * (1L << Math.max(0, attempt - 1)));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractConnectorErrorDetail(String responseBody) {
        if (isBlank(responseBody)) {
            return "";
        }

        String errorDescription = extractJsonStringField(responseBody, "error_description");
        if (!isBlank(errorDescription)) {
            return errorDescription;
        }

        String message = extractJsonStringField(responseBody, "message");
        if (!isBlank(message)) {
            return message;
        }

        String error = extractJsonStringField(responseBody, "error");
        if (!isBlank(error)) {
            return error;
        }

        String compact = responseBody.replace("\r", " ").replace("\n", " ").trim();
        return compact.length() > 220 ? compact.substring(0, 220) : compact;
    }

    private String extractJsonStringField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private ZoneId resolveZone(String zone) {
        if (!isBlank(zone)) {
            try {
                return ZoneId.of(zone.trim());
            } catch (Exception ignored) {
                logger.debug("Invalid timezone '{}', using system default", zone);
            }
        }
        return ZoneId.systemDefault();
    }

    private RishiTaskDto toTaskDto(RishiPracticeTask task) {
        return RishiTaskDto.builder()
                .id(task.getId())
                .sourceType(task.getSourceType())
                .title(task.getTitle())
                .details(task.getDetails())
                .topic(task.getTopic())
                .priority(task.getPriority())
                .status(task.getStatus())
                .suggestedMinutes(task.getSuggestedMinutes())
                .plannedStartAt(formatDateTime(task.getPlannedStartAt()))
                .plannedEndAt(formatDateTime(task.getPlannedEndAt()))
                .calendarEventLink(nullToEmpty(task.getCalendarEventLink()))
                .updatedAt(formatDateTime(task.getUpdatedAt()))
                .build();
    }

    private RishiGithubAnalyticsDto toGithubAnalyticsDto(RishiGithubAnalyticsSnapshot snapshot) {
        return RishiGithubAnalyticsDto.builder()
                .githubUsername(nullToEmpty(snapshot.getGithubUsername()))
                .windowDays(snapshot.getWindowDays() == null ? DEFAULT_GITHUB_WINDOW_DAYS : snapshot.getWindowDays())
                .commitCount(snapshot.getCommitCount() == null ? 0 : snapshot.getCommitCount())
                .pullRequestCount(snapshot.getPullRequestCount() == null ? 0 : snapshot.getPullRequestCount())
                .reviewCount(snapshot.getReviewCount() == null ? 0 : snapshot.getReviewCount())
                .issueCount(snapshot.getIssueCount() == null ? 0 : snapshot.getIssueCount())
                .activeRepoCount(snapshot.getActiveRepoCount() == null ? 0 : snapshot.getActiveRepoCount())
                .totalStars(snapshot.getTotalStars() == null ? 0 : snapshot.getTotalStars())
                .topLanguages(nullToEmpty(snapshot.getTopLanguages()))
                .capturedAt(formatDateTime(snapshot.getCreatedAt()))
                .build();
    }

    private RishiLeetCodeAnalyticsDto toLeetCodeAnalyticsDto(RishiLeetCodeAnalyticsSnapshot snapshot) {
        return RishiLeetCodeAnalyticsDto.builder()
                .leetcodeUsername(nullToEmpty(snapshot.getLeetcodeUsername()))
                .windowDays(snapshot.getWindowDays() == null ? DEFAULT_LEETCODE_WINDOW_DAYS : snapshot.getWindowDays())
                .totalSolved(snapshot.getTotalSolved() == null ? 0 : snapshot.getTotalSolved())
                .easySolved(snapshot.getEasySolved() == null ? 0 : snapshot.getEasySolved())
                .mediumSolved(snapshot.getMediumSolved() == null ? 0 : snapshot.getMediumSolved())
                .hardSolved(snapshot.getHardSolved() == null ? 0 : snapshot.getHardSolved())
                .ranking(snapshot.getRanking() == null ? 0 : snapshot.getRanking())
                .reputation(snapshot.getReputation() == null ? 0 : snapshot.getReputation())
                .contestRating(snapshot.getContestRating() == null ? 0.0 : snapshot.getContestRating())
                .contestAttendedCount(snapshot.getContestAttendedCount() == null ? 0 : snapshot.getContestAttendedCount())
                .solvedLast7d(snapshot.getSolvedLast7d() == null ? 0 : snapshot.getSolvedLast7d())
                .solvedPrev7d(snapshot.getSolvedPrev7d() == null ? 0 : snapshot.getSolvedPrev7d())
                .solveTrendPct(snapshot.getSolveTrendPct() == null ? 0.0 : snapshot.getSolveTrendPct())
                .weakTopics(nullToEmpty(snapshot.getWeakTopics()))
                .strongTopics(nullToEmpty(snapshot.getStrongTopics()))
                .capturedAt(formatDateTime(snapshot.getCreatedAt()))
                .build();
    }

    private RishiCodeforcesAnalyticsDto toCodeforcesAnalyticsDto(RishiCodeforcesAnalyticsSnapshot snapshot) {
        return RishiCodeforcesAnalyticsDto.builder()
                .codeforcesHandle(nullToEmpty(snapshot.getCodeforcesHandle()))
                .windowDays(snapshot.getWindowDays() == null ? DEFAULT_CODEFORCES_WINDOW_DAYS : snapshot.getWindowDays())
                .currentRating(snapshot.getCurrentRating() == null ? 0 : snapshot.getCurrentRating())
                .maxRating(snapshot.getMaxRating() == null ? 0 : snapshot.getMaxRating())
                .rank(nullToEmpty(snapshot.getRank()))
                .maxRank(nullToEmpty(snapshot.getMaxRank()))
                .contestCount(snapshot.getContestCount() == null ? 0 : snapshot.getContestCount())
                .solvedTotal(snapshot.getSolvedTotal() == null ? 0 : snapshot.getSolvedTotal())
                .solvedCurrentWindow(snapshot.getSolvedCurrentWindow() == null ? 0 : snapshot.getSolvedCurrentWindow())
                .solvedPreviousWindow(snapshot.getSolvedPreviousWindow() == null ? 0 : snapshot.getSolvedPreviousWindow())
                .solveTrendPct(snapshot.getSolveTrendPct() == null ? 0.0 : snapshot.getSolveTrendPct())
                .strongTags(nullToEmpty(snapshot.getStrongTags()))
                .weakTags(nullToEmpty(snapshot.getWeakTags()))
                .capturedAt(formatDateTime(snapshot.getCreatedAt()))
                .build();
    }

    private RishiTogglFocusDto toTogglFocusDto(RishiTogglFocusSnapshot snapshot) {
        return RishiTogglFocusDto.builder()
                .windowDays(snapshot.getWindowDays() == null ? DEFAULT_TOGGL_WINDOW_DAYS : snapshot.getWindowDays())
                .trackedMinutes(snapshot.getTrackedMinutes() == null ? 0L : snapshot.getTrackedMinutes())
                .entryCount(snapshot.getEntryCount() == null ? 0 : snapshot.getEntryCount())
                .capturedAt(formatDateTime(snapshot.getCreatedAt()))
                .build();
    }

    private int countAcceptedSubmissionsInWindow(
            List<Map<String, Object>> submissions,
            int fromDaysAgoInclusive,
            int toDaysAgoExclusive) {
        if (submissions == null || submissions.isEmpty()) {
            return 0;
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        long windowStart = nowEpochSeconds - (long) fromDaysAgoInclusive * 24L * 60L * 60L;
        long windowEnd = nowEpochSeconds - (long) toDaysAgoExclusive * 24L * 60L * 60L;

        int count = 0;
        for (Map<String, Object> submission : submissions) {
            long timestamp = parseLong(submission.get("timestamp"), 0L);
            if (timestamp >= windowStart && timestamp < windowEnd) {
                count++;
            }
        }
        return count;
    }

    private double computeSolveTrendPct(int solvedLast7d, int solvedPrev7d) {
        if (solvedPrev7d <= 0) {
            return solvedLast7d <= 0 ? 0.0 : 100.0;
        }
        return round2(((solvedLast7d - solvedPrev7d) * 100.0) / solvedPrev7d);
    }

    private String summarizeLeetCodeTopics(List<Map<String, Object>> algorithmMastery, boolean weakest) {
        if (algorithmMastery == null || algorithmMastery.isEmpty()) {
            return "";
        }

        Comparator<Map<String, Object>> comparator = Comparator
                .comparingDouble((Map<String, Object> row) -> parseDouble(row.get("masteryPercent"), 0.0))
                .thenComparingInt(row -> parseInt(row.get("problemsSolved"), 0))
                .thenComparing(row -> asString(row.get("tagName")));
        if (!weakest) {
            comparator = comparator.reversed();
        }

        return algorithmMastery.stream()
                .filter(row -> !isBlank(asString(row.get("tagName"))))
                .sorted(comparator)
                .limit(4)
                .map(this::formatLeetCodeTopicLabel)
                .collect(Collectors.joining(", "));
    }

    private String formatLeetCodeTopicLabel(Map<String, Object> row) {
        String topic = asString(row.get("tagName")).trim();
        double mastery = round2(parseDouble(row.get("masteryPercent"), 0.0));
        return topic + " (" + mastery + "%)";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private String normalizeMode(String requestedMode) {
        String mode = nullToEmpty(requestedMode).trim().toLowerCase(Locale.ROOT);
        return MODE_AGENT.equals(mode) ? MODE_AGENT : MODE_CHAT;
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.atOffset(ZoneOffset.UTC).toString();
    }

    private LocalDateTime parseDateTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }
        return null;
    }

    private int parseInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private long parseLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double parseDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requireConfigured(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalStateException(fieldName + " is not configured.");
        }
    }
}
