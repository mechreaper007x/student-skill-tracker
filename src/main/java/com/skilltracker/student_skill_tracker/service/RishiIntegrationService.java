package com.skilltracker.student_skill_tracker.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.skilltracker.student_skill_tracker.dto.RishiIntegrationStatusResponse;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthCompleteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthUrlResponse;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleItemDto;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleRequest;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleResponse;
import com.skilltracker.student_skill_tracker.dto.RishiTaskDto;
import com.skilltracker.student_skill_tracker.model.RishiPracticeTask;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiPracticeTaskRepository;
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

    private final RestTemplate restTemplate;
    private final TokenCryptoService tokenCryptoService;
    private final StudentRepository studentRepository;
    private final RishiPracticeTaskRepository taskRepository;

    @Value("${rishi.integrations.google.client-id:}")
    private String googleClientId;

    @Value("${rishi.integrations.google.client-secret:}")
    private String googleClientSecret;

    @Value("${rishi.integrations.google.calendar-id:primary}")
    private String defaultGoogleCalendarId;

    public RishiIntegrationService(
            RestTemplate restTemplate,
            TokenCryptoService tokenCryptoService,
            StudentRepository studentRepository,
            RishiPracticeTaskRepository taskRepository) {
        this.restTemplate = restTemplate;
        this.tokenCryptoService = tokenCryptoService;
        this.studentRepository = studentRepository;
        this.taskRepository = taskRepository;
    }

    public RishiIntegrationStatusResponse getStatus(Student student) {
        List<RishiPracticeTask> tasks = taskRepository.findByStudentOrderByUpdatedAtDesc(student);
        int pendingCount = (int) tasks.stream()
                .filter(task -> !STATUS_DONE.equalsIgnoreCase(task.getStatus()))
                .count();

        List<RishiTaskDto> latest = tasks.stream()
                .sorted(Comparator.comparing(RishiPracticeTask::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(this::toTaskDto)
                .toList();

        return RishiIntegrationStatusResponse.builder()
                .mode(normalizeMode(student.getRishiMode()))
                .hasApiKey(!isBlank(student.getAiApiKeyEncrypted()))
                .googleCalendarConnected(isGoogleCalendarConnected(student))
                .googleCalendarId(nullToEmpty(student.getRishiGoogleCalendarId()))
                .taskCount(tasks.size())
                .pendingTaskCount(pendingCount)
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

        List<RishiPracticeTask> pending = taskRepository.findByStudentAndStatusInOrderByPriorityDescUpdatedAtAsc(
                student,
                List.of(STATUS_TODO, STATUS_IN_PROGRESS),
                PageRequest.of(0, count));

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

    public boolean isGoogleCalendarConnected(Student student) {
        return !isBlank(student.getRishiGoogleAccessTokenEncrypted());
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
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, method, entity, Map.class);
            Map<String, Object> body = response.getBody();
            return body == null ? Map.of() : (Map<String, Object>) body;
        } catch (RestClientException ex) {
            logger.error("Integration request failed: {} {} -> {}", method, url, ex.getMessage());
            throw new IllegalStateException("Integration request failed. Check connector configuration and retry.");
        }
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
