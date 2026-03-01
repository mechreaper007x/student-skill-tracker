package com.skilltracker.student_skill_tracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RishiSessionEndRequest {
    private String reason;
    private Long activeDurationMs;
}

