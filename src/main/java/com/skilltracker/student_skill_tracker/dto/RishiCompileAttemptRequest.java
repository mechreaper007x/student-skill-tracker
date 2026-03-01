package com.skilltracker.student_skill_tracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiCompileAttemptRequest {
    private Boolean success;
    private Long executionTimeMs;
    private String language;
    private String problemSlug;
}

