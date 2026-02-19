package com.skilltracker.student_skill_tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeetCodeSubmissionRequest {
    private String sourceCode;
    private String language;
    private String problemSlug;
    private boolean waitForResult = true;
}
