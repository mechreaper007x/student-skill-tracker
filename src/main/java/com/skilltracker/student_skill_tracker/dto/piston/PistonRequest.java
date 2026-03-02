package com.skilltracker.student_skill_tracker.dto.piston;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PistonRequest {
    private String language;
    private String version;
    private List<PistonFile> files;
    private String stdin;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PistonFile {
        private String name;
        private String content;
    }
}
