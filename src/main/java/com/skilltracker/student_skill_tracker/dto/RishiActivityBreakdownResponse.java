package com.skilltracker.student_skill_tracker.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RishiActivityBreakdownResponse {
    private int days;
    private long typingMinutes;
    private long cursorIdleMinutes;
    private long editorUnfocusedMinutes;
    private long tabHiddenMinutes;
    private long activeMinutes;
    private long totalTrackedMinutes;
    private double typingSharePct;
    private double cursorIdleSharePct;
    private double editorUnfocusedSharePct;
    private double tabHiddenSharePct;

    @Builder.Default
    private List<RishiActivityDailyTrendDto> dailyTrends = new ArrayList<>();
}

