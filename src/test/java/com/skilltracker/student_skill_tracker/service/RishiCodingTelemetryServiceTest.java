package com.skilltracker.student_skill_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.skilltracker.student_skill_tracker.dto.RishiAttemptHistoryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiCompileAttemptAnalysisResponse;
import com.skilltracker.student_skill_tracker.dto.RishiCompileAttemptRequest;
import com.skilltracker.student_skill_tracker.dto.RishiSessionEndRequest;
import com.skilltracker.student_skill_tracker.model.RishiCodeChangeEvent;
import com.skilltracker.student_skill_tracker.model.RishiCodingSession;
import com.skilltracker.student_skill_tracker.model.RishiCompileAttemptLog;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.RishiCodeChangeEventRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCodingSessionRepository;
import com.skilltracker.student_skill_tracker.repository.RishiCompileAttemptLogRepository;

@ExtendWith(MockitoExtension.class)
class RishiCodingTelemetryServiceTest {

    @Mock
    private RishiCodingSessionRepository sessionRepository;
    @Mock
    private RishiCodeChangeEventRepository changeEventRepository;
    @Mock
    private RishiCompileAttemptLogRepository compileAttemptLogRepository;

    @InjectMocks
    private RishiCodingTelemetryService service;

    private Student student;
    private RishiCodingSession session;

    @BeforeEach
    void setUp() {
        student = Student.builder()
                .id(1L)
                .name("Rishi User")
                .email("rishi@example.com")
                .password("encoded")
                .leetcodeUsername("rishi-lc")
                .build();

        session = RishiCodingSession.builder()
                .id(10L)
                .student(student)
                .language("java")
                .problemSlug("two-sum")
                .startedAt(LocalDateTime.now().minusMinutes(15))
                .lastActivityAt(LocalDateTime.now().minusMinutes(15))
                .totalChangeEvents(1)
                .build();
    }

    @Test
    void recordCompileAttemptShouldClassifyCompileFailureBucket() {
        when(sessionRepository.findByIdAndStudent(eq(10L), eq(student))).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RishiCodingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(compileAttemptLogRepository.save(any(RishiCompileAttemptLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RishiCompileAttemptRequest request = new RishiCompileAttemptRequest();
        request.setSuccess(false);
        request.setSource("battle_run_local");
        request.setErrorMessage("cannot find symbol fooBar");
        request.setSubmissionStatus("Compile Error");

        RishiCompileAttemptAnalysisResponse response = service.recordCompileAttempt(student, 10L, request);

        assertEquals("COMPILATION_ERROR", response.getMistakeCategory());
        assertEquals("COMPILE_FAILURE", response.getFailureBucket());
        assertFalse(response.getNextSteps().isEmpty());

        ArgumentCaptor<RishiCompileAttemptLog> logCaptor = ArgumentCaptor.forClass(RishiCompileAttemptLog.class);
        verify(compileAttemptLogRepository).save(logCaptor.capture());
        assertEquals("COMPILE_FAILURE", logCaptor.getValue().getFailureBucket());
        assertNotNull(logCaptor.getValue().getErrorSnippet());
    }

    @Test
    void endSessionShouldPersistDetailedActivityDurations() {
        when(sessionRepository.findByIdAndStudent(eq(10L), eq(student))).thenReturn(Optional.of(session));
        when(changeEventRepository.findBySessionOrderByOccurredAtAsc(eq(session))).thenReturn(List.<RishiCodeChangeEvent>of());
        when(sessionRepository.save(any(RishiCodingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RishiSessionEndRequest request = new RishiSessionEndRequest();
        request.setReason("manual");
        request.setTypingDurationMs(30_000L);
        request.setCursorIdleDurationMs(20_000L);
        request.setEditorUnfocusedDurationMs(10_000L);
        request.setTabHiddenDurationMs(5_000L);

        service.endSession(student, 10L, request);

        ArgumentCaptor<RishiCodingSession> sessionCaptor = ArgumentCaptor.forClass(RishiCodingSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        RishiCodingSession saved = sessionCaptor.getValue();
        assertNotNull(saved.getEndedAt());
        assertEquals(30_000L, saved.getTypingDurationMs());
        assertEquals(20_000L, saved.getCursorIdleDurationMs());
        assertEquals(10_000L, saved.getEditorUnfocusedDurationMs());
        assertEquals(5_000L, saved.getTabHiddenDurationMs());
        assertEquals(50_000L, saved.getActiveDurationMs());
    }

    @Test
    void getAttemptHistoryShouldReturnDailySourceAndHeatmapData() {
        LocalDateTime now = LocalDateTime.now();
        List<RishiCompileAttemptLog> currentLogs = List.of(
                RishiCompileAttemptLog.builder()
                        .attemptedAt(now.minusDays(1))
                        .attemptSource("leetcode_submit")
                        .success(false)
                        .accuracyPct(50.0)
                        .mistakeCategory("WRONG_ANSWER")
                        .analysisSummary("Wrong answer on hidden test.")
                        .build(),
                RishiCompileAttemptLog.builder()
                        .attemptedAt(now.minusDays(2))
                        .attemptSource("duel_run")
                        .success(true)
                        .accuracyPct(100.0)
                        .mistakeCategory("NONE")
                        .analysisSummary("Accepted.")
                        .build(),
                RishiCompileAttemptLog.builder()
                        .attemptedAt(now.minusDays(2))
                        .attemptSource("battle_run_local")
                        .success(false)
                        .accuracyPct(0.0)
                        .mistakeCategory("RUNTIME_ERROR")
                        .analysisSummary("Runtime crash.")
                        .build());

        List<RishiCompileAttemptLog> previousLogs = List.of(
                RishiCompileAttemptLog.builder()
                        .attemptedAt(now.minusDays(20))
                        .attemptSource("leetcode_submit")
                        .success(true)
                        .accuracyPct(100.0)
                        .mistakeCategory("NONE")
                        .analysisSummary("Accepted.")
                        .build());

        when(compileAttemptLogRepository.findBySessionStudentAndAttemptedAtBetweenOrderByAttemptedAtDesc(
                eq(student), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(currentLogs, previousLogs);

        RishiAttemptHistoryResponse response = service.getAttemptHistory(student, 14, 20);

        assertEquals(3, response.getTotalAttempts());
        assertFalse(response.getDailyTrends().isEmpty());
        assertFalse(response.getSourceBreakdown().isEmpty());
        assertFalse(response.getCategoryHeatmap().isEmpty());
        assertTrue(response.getRecentAttempts().stream().anyMatch(a -> "TEST_FAILURE".equals(a.getFailureBucket())));
    }
}

