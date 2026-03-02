package com.skilltracker.student_skill_tracker.dto.jdoodle;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JDoodleRequest {
    private String clientId;
    private String clientSecret;
    private String script;
    private String language;
    private String versionIndex;
    private String stdin;
}
