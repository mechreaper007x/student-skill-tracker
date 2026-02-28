package com.skilltracker.student_skill_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionRequest {
    private String sourceCode;
    private String language;
    private String input;
    private int timeoutSeconds = 10;
    private String problemSlug; // optional: links execution to a specific LeetCode problem
}
