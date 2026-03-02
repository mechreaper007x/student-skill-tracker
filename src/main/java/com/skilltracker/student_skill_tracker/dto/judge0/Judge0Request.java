package com.skilltracker.student_skill_tracker.dto.judge0;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Judge0Request {
    private String source_code;
    private int language_id;
    private String stdin;
    private String expected_output;
}
