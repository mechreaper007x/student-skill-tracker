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
    private String googleCalendarId;
    private int taskCount;
    private int pendingTaskCount;
    @Builder.Default
    private List<RishiTaskDto> latestTasks = new ArrayList<>();
}
