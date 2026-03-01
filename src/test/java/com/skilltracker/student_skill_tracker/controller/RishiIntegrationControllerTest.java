package com.skilltracker.student_skill_tracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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

import com.skilltracker.student_skill_tracker.dto.RishiCoachingSummaryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiIntegrationStatusResponse;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthCompleteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleResponse;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.RishiIntegrationService;

@ExtendWith(MockitoExtension.class)
class RishiIntegrationControllerTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private RishiIntegrationService integrationService;

    private RishiIntegrationController controller;
    private Student student;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        controller = new RishiIntegrationController(studentRepository, integrationService);
        student = Student.builder()
                .id(1L)
                .name("Rishi")
                .email("rishi@example.com")
                .password("encoded")
                .leetcodeUsername("rishi")
                .build();
        auth = new UsernamePasswordAuthenticationToken("rishi@example.com", "x");
        when(studentRepository.findByEmailIgnoreCase(eq("rishi@example.com"))).thenReturn(Optional.of(student));
    }

    @Test
    void completeOAuthShouldReturnBadRequestWhenServiceThrows() {
        when(integrationService.completeOAuth(eq(student), any(RishiOAuthCompleteRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid oauth state."));

        RishiOAuthCompleteRequest request = new RishiOAuthCompleteRequest();
        request.setCode("code");
        request.setState("state");
        request.setRedirectUri("http://localhost:4200/advisor");

        var response = controller.completeOAuth(auth, request);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void scheduleCalendarShouldReturnResponse() {
        when(integrationService.scheduleNextBlocks(eq(student), any()))
                .thenReturn(RishiScheduleResponse.builder().scheduledCount(2).build());

        var response = controller.scheduleCalendar(auth, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void autoRescheduleCalendarShouldReturnResponse() {
        when(integrationService.autoRescheduleMissedBlocks(eq(student), any()))
                .thenReturn(RishiScheduleResponse.builder().scheduledCount(1).build());

        var response = controller.autoRescheduleCalendar(auth, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void coachingSummaryShouldReturnResponse() {
        when(integrationService.getCoachingSummary(eq(student), eq(7)))
                .thenReturn(RishiCoachingSummaryResponse.builder().windowDays(7).recommendedFocus("Graphs").build());

        var response = controller.getCoachingSummary(auth, 7);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getStatusShouldReturnResponse() {
        when(integrationService.getStatus(eq(student)))
                .thenReturn(RishiIntegrationStatusResponse.builder().mode("agent").build());

        var response = controller.getStatus(auth);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}

