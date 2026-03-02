package com.skilltracker.student_skill_tracker.dto.jdoodle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JDoodleResponse {
    private String output;
    private int statusCode;
    private String memory;
    private String cpuTime;
    private String error;
}
