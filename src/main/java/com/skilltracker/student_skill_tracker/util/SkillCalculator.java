package com.skilltracker.student_skill_tracker.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkillCalculator {

    @Value("${scoring.problem-solving.divisor}")
    private double psDivisor;

    @Value("${scoring.algorithms.medium-weight}")
    private double algoMediumWeight;

    @Value("${scoring.algorithms.hard-weight}")
    private double algoHardWeight;

    @Value("${scoring.algorithms.divisor}")
    private double algoDivisor;

    @Value("${scoring.data-structures.easy-weight}")
    private double dsEasyWeight;

    @Value("${scoring.data-structures.medium-weight}")
    private double dsMediumWeight;

    @Value("${scoring.data-structures.divisor}")
    private double dsDivisor;

    public double calculateProblemSolvingScore(int totalSolved) {
        return Math.min(100.0, (totalSolved / psDivisor) * 100.0);
    }

    public double calculateAlgorithmsScore(int medium, int hard) {
        return Math.min(100.0, ((medium * algoMediumWeight + hard * algoHardWeight) / algoDivisor) * 100.0);
    }

    public double calculateDataStructuresScore(int easy, int medium) {
        return Math.min(100.0, ((easy * dsEasyWeight + medium * dsMediumWeight) / dsDivisor) * 100.0);
    }

    // --- Cognitive Nexus / Humanistic Scoring ---

    /**
     * Reasoning Ability (Kahneman's System 2 Synthesis)
     * Rewards deliberate planning and first-attempt precision.
     */
    public double calculateReasoningScore(int accepted, int total, long avgPlanningMs) {
        if (total == 0)
            return 0.0;
        double base = (double) accepted / total * 100.0;

        // Planning bonus: +10 if planning time is > 1 min
        if (avgPlanningMs > 60000)
            base += 10.0;

        return Math.max(0.0, Math.min(100.0, base));
    }

    /**
     * Critical Thinking (Bloom's Meta-Cognitive Evaluation)
     * Rewards mental "dry-running" (high compilation success rate).
     */
    public double calculateCriticalThinkingScore(int successComp, int totalComp) {
        if (totalComp == 0)
            return 0.0;
        return ((double) successComp / totalComp) * 100.0;
    }

    /**
     * EQ / Self-Awareness (Goleman's Affective Regulation)
     * Penalizes "tilting" (very fast recovery after failure).
     */
    public double calculateEqScore(long avgRecoveryMs, int totalSubmissions) {
        if (totalSubmissions == 0)
            return 100.0; // High default

        // If recovery is < 30s on average, it suggests "tilting"
        double base = 100.0;
        if (avgRecoveryMs < 30000) {
            base -= (30000 - avgRecoveryMs) / 300.0; // Scaled penalty
        }

        return Math.max(20.0, Math.min(100.0, base));
    }

    public double calculateProblemSolvingHumanistic(int solved, int attempts) {
        if (attempts == 0)
            return 0.0;
        // Resilience: High if they struggle but solve
        double ratio = (double) solved / attempts;
        return (ratio * 50.0) + (Math.min(solved, 50) * 1.0); // Reward both volume and precision
    }
}