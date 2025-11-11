package com.skilltracker.student_skill_tracker.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdvisorResult {
    // short human string for UI headline
    private String headline;

    // explanation of current state
    private String summary;

    // micro actions — tiny things to do right now (5-20 minutes)
    private List<String> microActions;

    // daily routine (30-60 minutes)
    private List<String> dailyPlan;

    // weekly milestones (larger)
    private List<String> weeklyGoals;

    // 1-3 month growth trajectory
    private List<String> longTermPlan;

    // prioritized topics to study (most important first)
    private List<String> priorities;

    // optional: links/resources (URL strings)
    private List<String> resources;

    // numeric confidence 0..1
    private double confidence;

    // explanation of the confidence score
    private String confidenceRationale;

    // human-readable rationale or debug notes (optional, hidden in prod)
    private String rationale;
}
