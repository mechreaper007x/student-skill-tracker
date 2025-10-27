package com.skilltracker.student_skill_tracker.util;

public class SkillCalculator {

    public static double problemSolvingScore(int totalSolved) {
        return Math.min(100.0, (totalSolved / 500.0) * 100.0);
    }

    public static double algorithmsScore(int medium, int hard) {
        return Math.min(100.0, ((medium * 0.6 + hard * 1.0) / 200.0) * 100.0);
    }

    public static double dataStructuresScore(int easy, int medium) {
        return Math.min(100.0, ((easy * 0.4 + medium * 0.8) / 250.0) * 100.0);
    }
}
