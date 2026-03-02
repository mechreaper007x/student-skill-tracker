package com.skilltracker.student_skill_tracker.dto.piston;

import lombok.Data;

@Data
public class PistonResponse {
    private String language;
    private String version;
    private PistonResult run;

    @Data
    public static class PistonResult {
        private String stdout;
        private String stderr;
        private int code;
        private String signal;
        private String output;
    }
}
