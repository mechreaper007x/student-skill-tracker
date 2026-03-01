package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skilltracker.student_skill_tracker.dto.RishiCodeChangeBatchRequest;
import com.skilltracker.student_skill_tracker.dto.RishiCodeChangeEventDto;
import com.skilltracker.student_skill_tracker.dto.RishiCompileAttemptRequest;
import com.skilltracker.student_skill_tracker.dto.RishiGrowthMetrics;
import com.skilltracker.student_skill_tracker.dto.RishiGrowthSummaryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiSessionEndRequest;
import com.skilltracker.student_skill_tracker.dto.RishiSessionStartRequest;
import com.skilltracker.student_skill_tracker.dto.RishiSessionStartResponse;
import com.skilltracker.student_skill_tracker.model.RishiCodeChangeEvent;
import com.skilltracker.student_skill_tracker.model.RishiCodingSession;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiCodeChangeEventRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCodingSessionRepository;

@Service
public class RishiCodingTelemetryService {

    private final RishiCodingSessionRepository sessionRepository;
    private final RishiCodeChangeEventRepository changeEventRepository;

    public RishiCodingTelemetryService(
            RishiCodingSessionRepository sessionRepository,
            RishiCodeChangeEventRepository changeEventRepository) {
        this.sessionRepository = sessionRepository;
        this.changeEventRepository = changeEventRepository;
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
    public void recordCompileAttempt(Student student, Long sessionId, RishiCompileAttemptRequest request) {
        RishiCodingSession session = getOwnedSession(student, sessionId);
        if (session.getEndedAt() != null) {
            return;
        }

        boolean success = request != null && Boolean.TRUE.equals(request.getSuccess());
        LocalDateTime now = LocalDateTime.now();
        session.setCompileAttempts(session.getCompileAttempts() + 1);
        session.setLanguage(safeLanguage(request != null ? request.getLanguage() : session.getLanguage()));
        String requestSlug = safeProblemSlug(request != null ? request.getProblemSlug() : null);
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

        Long requestedActiveMs = request != null ? request.getActiveDurationMs() : null;
        if (requestedActiveMs != null) {
            long sanitized = Math.max(0L, requestedActiveMs);
            session.setActiveDurationMs(Math.min(sanitized, session.getTotalDurationMs()));
        }

        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public RishiGrowthSummaryResponse getGrowthSummary(Student student, int days) {
        int safeDays = Math.max(7, Math.min(60, days));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(safeDays);
        LocalDateTime previousStart = currentStart.minusDays(safeDays);

        List<RishiCodingSession> sessions = sessionRepository.findByStudentAndStartedAtBetween(student, previousStart, now);

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
                .codingMinutesGrowthPct(growthPct(currentMetrics.getTotalCodingMinutes(), previousMetrics.getTotalCodingMinutes()))
                .successRateGrowthPct(growthPct(currentMetrics.getCompileSuccessRate(), previousMetrics.getCompileSuccessRate()))
                .firstSuccessSpeedGrowthPct(reverseGrowthPct(
                        currentMetrics.getAverageFirstSuccessSeconds(),
                        previousMetrics.getAverageFirstSuccessSeconds()))
                .consistencyGrowthPct(growthPct(currentMetrics.getSessions(), previousMetrics.getSessions()))
                .build();
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
            totalDurationMs += session.getTotalDurationMs() != null ? session.getTotalDurationMs() : 0L;
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

