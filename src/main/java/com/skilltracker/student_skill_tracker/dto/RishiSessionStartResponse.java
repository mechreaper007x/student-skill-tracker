package com.skilltracker.student_skill_tracker.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiSessionStartResponse {
    private Long sessionId;
    private LocalDateTime startedAt;
}

