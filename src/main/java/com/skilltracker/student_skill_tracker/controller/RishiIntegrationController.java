package com.skilltracker.student_skill_tracker.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skilltracker.student_skill_tracker.dto.RishiIntegrationStatusResponse;
import com.skilltracker.student_skill_tracker.dto.RishiCoachingSummaryResponse;
import com.skilltracker.student_skill_tracker.dto.RishiCodeforcesAnalyticsDto;
import com.skilltracker.student_skill_tracker.dto.RishiCodeforcesAnalyticsSyncRequest;
import com.skilltracker.student_skill_tracker.dto.RishiCodeforcesHandleRequest;
import com.skilltracker.student_skill_tracker.dto.RishiGithubAnalyticsDto;
import com.skilltracker.student_skill_tracker.dto.RishiGithubAnalyticsSyncRequest;
import com.skilltracker.student_skill_tracker.dto.RishiLeetCodeAnalyticsDto;
import com.skilltracker.student_skill_tracker.dto.RishiLeetCodeAnalyticsSyncRequest;
import com.skilltracker.student_skill_tracker.dto.RishiModeRequest;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthCompleteRequest;
import com.skilltracker.student_skill_tracker.dto.RishiOAuthUrlResponse;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleRequest;
import com.skilltracker.student_skill_tracker.dto.RishiScheduleResponse;
import com.skilltracker.student_skill_tracker.dto.RishiTogglFocusDto;
import com.skilltracker.student_skill_tracker.dto.RishiTogglFocusSyncRequest;
import com.skilltracker.student_skill_tracker.dto.RishiTogglTokenRequest;
import com.skilltracker.student_skill_tracker.model.Student;
import com.skilltracker.student_skill_tracker.repository.StudentRepository;
import com.skilltracker.student_skill_tracker.service.RishiIntegrationService;

@RestController
@RequestMapping("/api/rishi/integrations")
public class RishiIntegrationController {

    private final StudentRepository studentRepository;
    private final RishiIntegrationService rishiIntegrationService;

    public RishiIntegrationController(
            StudentRepository studentRepository,
            RishiIntegrationService rishiIntegrationService) {
        this.studentRepository = studentRepository;
        this.rishiIntegrationService = rishiIntegrationService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        RishiIntegrationStatusResponse response = rishiIntegrationService.getStatus(studentOpt.get());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mode")
    public ResponseEntity<?> setMode(Authentication auth, @RequestBody(required = false) RishiModeRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        String mode = rishiIntegrationService.setMode(studentOpt.get(), request == null ? null : request.getMode());
        return ResponseEntity.ok(Map.of("mode", mode));
    }

    @GetMapping("/google-calendar/auth-url")
    public ResponseEntity<?> getGoogleAuthUrl(
            Authentication auth,
            @RequestParam("redirectUri") String redirectUri) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            RishiOAuthUrlResponse response = rishiIntegrationService.createGoogleAuthUrl(studentOpt.get(), redirectUri);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/oauth/complete")
    public ResponseEntity<?> completeOAuth(
            Authentication auth,
            @RequestBody(required = false) RishiOAuthCompleteRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            String provider = rishiIntegrationService.completeOAuth(studentOpt.get(), request);
            RishiIntegrationStatusResponse status = rishiIntegrationService.getStatus(studentOpt.get());
            return ResponseEntity.ok(Map.of("provider", provider, "status", status));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/google-calendar/schedule")
    public ResponseEntity<?> scheduleCalendar(
            Authentication auth,
            @RequestBody(required = false) RishiScheduleRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            RishiScheduleResponse response = rishiIntegrationService.scheduleNextBlocks(studentOpt.get(), request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/google-calendar/auto-reschedule")
    public ResponseEntity<?> autoRescheduleCalendar(
            Authentication auth,
            @RequestBody(required = false) RishiScheduleRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            RishiScheduleResponse response = rishiIntegrationService.autoRescheduleMissedBlocks(studentOpt.get(), request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/coaching-summary")
    public ResponseEntity<?> getCoachingSummary(
            Authentication auth,
            @RequestParam(name = "days", defaultValue = "7") int days) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            RishiCoachingSummaryResponse response = rishiIntegrationService.getCoachingSummary(studentOpt.get(), days);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/codeforces/handle")
    public ResponseEntity<?> setCodeforcesHandle(
            Authentication auth,
            @RequestBody(required = false) RishiCodeforcesHandleRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            String handle = request == null ? null : request.getHandle();
            String savedHandle = rishiIntegrationService.setCodeforcesHandle(studentOpt.get(), handle);
            return ResponseEntity.ok(Map.of("codeforcesHandle", savedHandle));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/toggl/token")
    public ResponseEntity<?> setTogglToken(
            Authentication auth,
            @RequestBody(required = false) RishiTogglTokenRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            String token = request == null ? null : request.getApiToken();
            rishiIntegrationService.setTogglToken(studentOpt.get(), token);
            return ResponseEntity.ok(Map.of("status", "connected"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/github/analytics/sync")
    public ResponseEntity<?> syncGithubAnalytics(
            Authentication auth,
            @RequestBody(required = false) RishiGithubAnalyticsSyncRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            Integer windowDays = request == null ? null : request.getWindowDays();
            RishiGithubAnalyticsDto response = rishiIntegrationService.syncGithubAnalytics(studentOpt.get(), windowDays);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/github/analytics/latest")
    public ResponseEntity<?> getLatestGithubAnalytics(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        return rishiIntegrationService.getLatestGithubAnalytics(studentOpt.get())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "githubUsername", nullToEmpty(studentOpt.get().getGithubUsername()),
                        "windowDays", 0,
                        "commitCount", 0,
                        "pullRequestCount", 0,
                        "reviewCount", 0,
                        "issueCount", 0,
                        "activeRepoCount", 0,
                        "totalStars", 0,
                        "topLanguages", "",
                        "capturedAt", "")));
    }

    @PostMapping("/leetcode/analytics/sync")
    public ResponseEntity<?> syncLeetCodeAnalytics(
            Authentication auth,
            @RequestBody(required = false) RishiLeetCodeAnalyticsSyncRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            Integer windowDays = request == null ? null : request.getWindowDays();
            RishiLeetCodeAnalyticsDto response = rishiIntegrationService.syncLeetCodeAnalytics(studentOpt.get(), windowDays);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/leetcode/analytics/latest")
    public ResponseEntity<?> getLatestLeetCodeAnalytics(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        return rishiIntegrationService.getLatestLeetCodeAnalytics(studentOpt.get())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Map<String, Object> empty = new LinkedHashMap<>();
                    empty.put("leetcodeUsername", nullToEmpty(studentOpt.get().getLeetcodeUsername()));
                    empty.put("windowDays", 0);
                    empty.put("totalSolved", 0);
                    empty.put("easySolved", 0);
                    empty.put("mediumSolved", 0);
                    empty.put("hardSolved", 0);
                    empty.put("ranking", 0);
                    empty.put("reputation", 0);
                    empty.put("contestRating", 0.0);
                    empty.put("contestAttendedCount", 0);
                    empty.put("solvedLast7d", 0);
                    empty.put("solvedPrev7d", 0);
                    empty.put("solveTrendPct", 0.0);
                    empty.put("weakTopics", "");
                    empty.put("strongTopics", "");
                    empty.put("capturedAt", "");
                    return ResponseEntity.ok(empty);
                });
    }

    @PostMapping("/codeforces/analytics/sync")
    public ResponseEntity<?> syncCodeforcesAnalytics(
            Authentication auth,
            @RequestBody(required = false) RishiCodeforcesAnalyticsSyncRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            Integer windowDays = request == null ? null : request.getWindowDays();
            RishiCodeforcesAnalyticsDto response = rishiIntegrationService.syncCodeforcesAnalytics(studentOpt.get(), windowDays);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/codeforces/analytics/latest")
    public ResponseEntity<?> getLatestCodeforcesAnalytics(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        return rishiIntegrationService.getLatestCodeforcesAnalytics(studentOpt.get())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Map<String, Object> empty = new LinkedHashMap<>();
                    empty.put("codeforcesHandle", nullToEmpty(studentOpt.get().getCodeforcesHandle()));
                    empty.put("windowDays", 0);
                    empty.put("currentRating", 0);
                    empty.put("maxRating", 0);
                    empty.put("rank", "");
                    empty.put("maxRank", "");
                    empty.put("contestCount", 0);
                    empty.put("solvedTotal", 0);
                    empty.put("solvedCurrentWindow", 0);
                    empty.put("solvedPreviousWindow", 0);
                    empty.put("solveTrendPct", 0.0);
                    empty.put("strongTags", "");
                    empty.put("weakTags", "");
                    empty.put("capturedAt", "");
                    return ResponseEntity.ok(empty);
                });
    }

    @PostMapping("/toggl/focus/sync")
    public ResponseEntity<?> syncTogglFocus(
            Authentication auth,
            @RequestBody(required = false) RishiTogglFocusSyncRequest request) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            Integer windowDays = request == null ? null : request.getWindowDays();
            RishiTogglFocusDto response = rishiIntegrationService.syncTogglFocus(studentOpt.get(), windowDays);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/toggl/focus/latest")
    public ResponseEntity<?> getLatestTogglFocus(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        return rishiIntegrationService.getLatestTogglFocus(studentOpt.get())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "windowDays", 0,
                        "trackedMinutes", 0,
                        "entryCount", 0,
                        "capturedAt", "")));
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> getTasks(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        return ResponseEntity.ok(rishiIntegrationService.getTasks(studentOpt.get()));
    }

    @PostMapping("/tasks/{taskId}/done")
    public ResponseEntity<?> markTaskDone(Authentication auth, @PathVariable Long taskId) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        try {
            rishiIntegrationService.markTaskDone(studentOpt.get(), taskId);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/google-calendar/disconnect")
    public ResponseEntity<?> disconnectGoogleCalendar(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        rishiIntegrationService.disconnectGoogleCalendar(studentOpt.get());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/toggl/disconnect")
    public ResponseEntity<?> disconnectToggl(Authentication auth) {
        Optional<Student> studentOpt = getCurrentStudent(auth);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Student not found"));
        }
        rishiIntegrationService.disconnectToggl(studentOpt.get());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private Optional<Student> getCurrentStudent(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return Optional.empty();
        }
        return studentRepository.findByEmailIgnoreCase(auth.getName());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
