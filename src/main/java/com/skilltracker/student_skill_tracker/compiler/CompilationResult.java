package com.skilltracker.student_skill_tracker.compiler;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationResult {
    private boolean success;
    private String output;
    private String error;
    private String executionTime;
    private String language;
    private LocalDateTime timestamp;

    public String getFormattedResult() {
        if (!success) {
            return "❌ Error:\n" + error;
        }
        return "✅ Success:\n" + output;
    }
}