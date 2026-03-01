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
public class RishiLeetCodeAnalyticsDto {
    private String leetcodeUsername;
    private int windowDays;
    private int totalSolved;
    private int easySolved;
    private int mediumSolved;
    private int hardSolved;
    private int ranking;
    private int reputation;
    private double contestRating;
    private int contestAttendedCount;
    private int solvedLast7d;
    private int solvedPrev7d;
    private double solveTrendPct;
    private String weakTopics;
    private String strongTopics;
    private String capturedAt;
}

