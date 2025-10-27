package com.skilltracker.student_skill_tracker.util;

public class AIAdvisor {

    public static String generateAdvice(double ps, double algo, double ds) {
        String weakest = (ps < algo && ps < ds) ? "Problem Solving" :
                         (algo < ps && algo < ds) ? "Algorithms" : "Data Structures";

        return String.format("""
                Your %s skill needs attention.
                Focus on practice in that area and aim for +10%% improvement this week.
                """, weakest);
    }
}
