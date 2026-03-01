package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiAttemptCategoryTrendDto {
    private String category;
    private long currentCount;
    private long previousCount;
    private double sharePct;
    private double trendPct;
}
