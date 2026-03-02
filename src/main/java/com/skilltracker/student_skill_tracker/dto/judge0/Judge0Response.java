package com.skilltracker.student_skill_tracker.dto.judge0;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Judge0Response {
    private String stdout;
    private String time;
    private int memory;
    private String stderr;
    private String token;
    private String compile_output;
    private String message;
    private Status status;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        private int id;
        private String description;
    }
}
