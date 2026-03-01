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
public class RishiGithubAnalyticsDto {
    private String githubUsername;
    private int windowDays;
    private int commitCount;
    private int pullRequestCount;
    private int reviewCount;
    private int issueCount;
    private int activeRepoCount;
    private int totalStars;
    private String topLanguages;
    private String capturedAt;
}

