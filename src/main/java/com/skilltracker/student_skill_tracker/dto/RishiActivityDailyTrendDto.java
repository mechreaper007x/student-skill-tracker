package com.skilltracker.student_skill_tracker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RishiActivityDailyTrendDto {
    private String date;
    private long typingMinutes;
    private long cursorIdleMinutes;
    private long editorUnfocusedMinutes;
    private long tabHiddenMinutes;
    private long activeMinutes;
}

