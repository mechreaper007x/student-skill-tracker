package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiAttemptCategoryHeatmapDto {
    private String category;
    private String source;
    private long attempts;
    private long failedAttempts;
}

