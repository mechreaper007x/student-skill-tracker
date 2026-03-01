package com.skilltracker.student_skill_tracker.dto;

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
public class RishiFocusMetricsDto {
    private int windowDays;
    private long plannedMinutes;
    private long actualMinutes;
    private int plannedSessions;
    private int actualSessions;
    private double adherencePct;
    private String dataSource;
}
