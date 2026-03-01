package com.skilltracker.student_skill_tracker.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiIntegrationStatusResponse {
    private String mode;
    private boolean hasApiKey;
    private boolean googleCalendarConnected;
    private boolean githubConnected;
    private boolean leetcodeConnected;
    private boolean codeforcesConnected;
    private boolean togglConnected;
    private String googleCalendarId;
    private String codeforcesHandle;
    private int taskCount;
    private int pendingTaskCount;
    private RishiGithubAnalyticsDto latestGithubAnalytics;
    private RishiLeetCodeAnalyticsDto latestLeetCodeAnalytics;
    private RishiCodeforcesAnalyticsDto latestCodeforcesAnalytics;
    private RishiTogglFocusDto latestTogglFocus;
    private RishiFocusMetricsDto focusMetrics;
    @Builder.Default
    private List<RishiTaskDto> latestTasks = new ArrayList<>();
}
