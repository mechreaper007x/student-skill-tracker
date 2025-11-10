package com.skilltracker.student_skill_tracker.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class AIAdvisor {

    private static final List<String> PS_ADVICE = List.of(
            "Try breaking down complex problems into smaller, manageable parts.",
            "Focus on understanding the problem constraints and edge cases before coding.",
            "Practice more medium-level problems on platforms like LeetCode.",
            "Consider the 'Divide and Conquer' strategy for complex problems.",
            "Work on recognizing common problem patterns."
    );

    private static final List<String> ALGO_ADVICE = List.of(
            "Review fundamental algorithms like sorting, searching, and graph traversal.",
            "Try to implement algorithms from scratch without relying on libraries.",
            "Focus on understanding the time and space complexity (Big O notation) of the algorithms you use.",
            "Practice problems that require dynamic programming.",
            "Study different algorithmic paradigms like greedy algorithms and backtracking."
    );

    private static final List<String> DS_ADVICE = List.of(
            "Revisit core data structures like arrays, linked lists, trees, and graphs.",
            "Solve problems that require specific data structures to see their real-world applications.",
            "Focus on understanding the trade-offs between different data structures.",
            "Implement complex data structures like heaps, tries, and segment trees.",
            "Practice with problems related to tree and graph traversals."
    );

    private static final Random RANDOM = new Random();

    public static String generateAdvice(double ps, double algo, double ds) {
        String weakestSkill;
        List<String> advicePool;

        if (ps <= algo && ps <= ds) {
            weakestSkill = "Problem Solving";
            advicePool = PS_ADVICE;
        } else if (algo <= ps && algo <= ds) {
            weakestSkill = "Algorithms";
            advicePool = ALGO_ADVICE;
        } else {
            weakestSkill = "Data Structures";
            advicePool = DS_ADVICE;
        }

    // make a mutable copy since the static lists are immutable (List.of)
    List<String> shuffled = new ArrayList<>(advicePool);
    Collections.shuffle(shuffled);
    List<String> selectedAdvice = shuffled.stream().limit(RANDOM.nextInt(2) + 2).collect(Collectors.toList());

        StringBuilder detailedReview = new StringBuilder();
        detailedReview.append(String.format("Your primary area for improvement is %s. Here is a detailed review:\n", weakestSkill));
        for (String advice : selectedAdvice) {
            detailedReview.append(String.format("- %s\n", advice));
        }
        detailedReview.append("\nKeep practicing, and you will see significant improvement!");

        return detailedReview.toString();
    }
}
