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
public class RishiCoachingSummaryResponse {
    private int windowDays;
    private int pendingTasks;
    private int missedTasks;
    private long plannedMinutes;
    private long actualMinutes;
    private double adherencePct;
    private int recommendedMinutesToday;
    private String recommendedFocus;
    private String summary;
    private String generatedAt;

    @Builder.Default
    private List<String> nextActions = new ArrayList<>();
}

