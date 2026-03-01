package com.skilltracker.student_skill_tracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.skilltracker.student_skill_tracker.dto.RishiActivityBreakdownResponse;
import com.skilltracker.student_skill_tracker.dto.RishiAttemptHistoryResponse;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.RishiCodingTelemetryService;

@ExtendWith(MockitoExtension.class)
class RishiCodingTelemetryControllerTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private RishiCodingTelemetryService telemetryService;

    private RishiCodingTelemetryController controller;
    private Student student;

    @BeforeEach
    void setUp() {
        controller = new RishiCodingTelemetryController(studentRepository, telemetryService);
        student = Student.builder()
                .id(1L)
                .name("Rishi")
                .email("rishi@example.com")
                .password("encoded")
                .leetcodeUsername("rishi")
                .build();
    }

    @Test
    void getAttemptHistoryShouldReturnUnauthorizedWhenStudentMissing() {
        Authentication auth = new UsernamePasswordAuthenticationToken("missing@example.com", "x");
        when(studentRepository.findByEmailIgnoreCase(eq("missing@example.com"))).thenReturn(Optional.empty());

        var response = controller.getAttemptHistory(auth, 14, 20);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void exportAttemptHistoryShouldReturnCsvAttachment() {
        Authentication auth = new UsernamePasswordAuthenticationToken("rishi@example.com", "x");
        when(studentRepository.findByEmailIgnoreCase(eq("rishi@example.com"))).thenReturn(Optional.of(student));
        when(telemetryService.exportAttemptHistoryCsv(eq(student), eq(14))).thenReturn("col1,col2\nx,y");

        var response = controller.exportAttemptHistoryCsv(auth, 14);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/csv", response.getHeaders().getContentType().toString());
        assertNotNull(response.getHeaders().getFirst("Content-Disposition"));
    }

    @Test
    void getActivityBreakdownShouldReturnPayload() {
        Authentication auth = new UsernamePasswordAuthenticationToken("rishi@example.com", "x");
        when(studentRepository.findByEmailIgnoreCase(eq("rishi@example.com"))).thenReturn(Optional.of(student));
        when(telemetryService.getActivityBreakdown(eq(student), eq(14)))
                .thenReturn(RishiActivityBreakdownResponse.builder().days(14).typingMinutes(10).build());

        var response = controller.getActivityBreakdown(auth, 14);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getAttemptHistoryShouldReturnPayload() {
        Authentication auth = new UsernamePasswordAuthenticationToken("rishi@example.com", "x");
        when(studentRepository.findByEmailIgnoreCase(eq("rishi@example.com"))).thenReturn(Optional.of(student));
        when(telemetryService.getAttemptHistory(eq(student), eq(14), eq(20)))
                .thenReturn(RishiAttemptHistoryResponse.builder().days(14).limit(20).totalAttempts(3).build());

        var response = controller.getAttemptHistory(auth, 14, 20);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}

