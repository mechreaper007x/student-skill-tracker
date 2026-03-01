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
public class RishiAttemptHistoryResponse {
    private int days;
    private int limit;
    private long totalAttempts;
    private long successfulAttempts;
    private double successRatePct;
    private double averageAccuracyPct;
    private double attemptsGrowthPct;
    private double accuracyGrowthPct;

    @Builder.Default
    private List<RishiAttemptCategoryTrendDto> categoryTrends = new ArrayList<>();

    @Builder.Default
    private List<RishiAttemptRecordDto> recentAttempts = new ArrayList<>();

    @Builder.Default
    private List<RishiAttemptDailyTrendDto> dailyTrends = new ArrayList<>();

    @Builder.Default
    private List<RishiAttemptSourceBreakdownDto> sourceBreakdown = new ArrayList<>();

    @Builder.Default
    private List<RishiAttemptCategoryHeatmapDto> categoryHeatmap = new ArrayList<>();
}
