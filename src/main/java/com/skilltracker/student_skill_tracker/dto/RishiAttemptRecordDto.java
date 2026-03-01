package com.skilltracker.student_skill_tracker.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiAttemptRecordDto {
    private LocalDateTime attemptedAt;
    private String source;
    private boolean success;
    private String failureBucket;
    private double accuracyPct;
    private String mistakeCategory;
    private String summary;
    private List<String> nextSteps;
}
