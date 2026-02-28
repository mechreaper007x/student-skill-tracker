package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiGrowthSummaryResponse {
    private int days;
    private RishiGrowthMetrics current;
    private RishiGrowthMetrics previous;
    private double codingMinutesGrowthPct;
    private double successRateGrowthPct;
    private double firstSuccessSpeedGrowthPct;
    private double consistencyGrowthPct;
}

