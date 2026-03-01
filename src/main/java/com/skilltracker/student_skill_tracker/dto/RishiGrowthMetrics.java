package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiGrowthMetrics {
    private int sessions;
    private long totalCodingMinutes;
    private int compileAttempts;
    private double compileSuccessRate;
    private double averageFirstSuccessSeconds;
    private double averageEditsPerSession;
}

