package com.skilltracker.student_skill_tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionRequest {

    @NotBlank(message = "Source code cannot be empty")
    @Size(max = 200_000, message = "Source code exceeds the maximum allowed size of 200KB")
    private String sourceCode;

    @NotBlank(message = "Language is required")
    private String language;

    @Size(max = 100_000, message = "Input text exceeds the maximum allowed size of 100KB")
    private String input;

    private int timeoutSeconds = 10;
    private String problemSlug; // optional: links execution to a specific LeetCode problem
}
