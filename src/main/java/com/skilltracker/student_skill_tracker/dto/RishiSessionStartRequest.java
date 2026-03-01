package com.skilltracker.student_skill_tracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiSessionStartRequest {
    private String language;
    private String problemSlug;
}

