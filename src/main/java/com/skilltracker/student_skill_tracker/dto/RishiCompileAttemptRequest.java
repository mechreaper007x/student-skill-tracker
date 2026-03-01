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
    private String source;
    private String errorMessage;
    private String outputPreview;
    private String submissionStatus;
    private String judgeMessage;
    private Integer testsPassed;
    private Integer testsTotal;
    private String failedTestInput;
    private String expectedOutput;
    private String actualOutput;
    private String stackTraceSnippet;
}
