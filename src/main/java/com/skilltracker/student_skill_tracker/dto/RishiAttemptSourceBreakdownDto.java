package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiAttemptSourceBreakdownDto {
    private String source;
    private long attempts;
    private long successfulAttempts;
    private double successRatePct;
    private double averageAccuracyPct;
}

