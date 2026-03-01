package com.skilltracker.student_skill_tracker.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiCompileAttemptAnalysisResponse {
    private String status;
    private boolean success;
    private String source;
    private String failureBucket;
    private String mistakeCategory;
    private double accuracyPct;
    private String summary;
    private List<String> nextSteps;
    private LocalDateTime recordedAt;
}
