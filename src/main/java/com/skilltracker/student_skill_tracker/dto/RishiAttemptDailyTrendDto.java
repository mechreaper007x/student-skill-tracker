package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiAttemptDailyTrendDto {
    private String date;
    private long attempts;
    private double successRatePct;
    private double averageAccuracyPct;
}

