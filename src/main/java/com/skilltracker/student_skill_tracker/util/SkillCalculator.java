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
}