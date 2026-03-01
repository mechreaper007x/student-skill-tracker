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
public class RishiCodeforcesAnalyticsDto {
    private String codeforcesHandle;
    private int windowDays;
    private int currentRating;
    private int maxRating;
    private String rank;
    private String maxRank;
    private int contestCount;
    private int solvedTotal;
    private int solvedCurrentWindow;
    private int solvedPreviousWindow;
    private double solveTrendPct;
    private String strongTags;
    private String weakTags;
    private String capturedAt;
}
