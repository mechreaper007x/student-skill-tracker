package com.skilltracker.student_skill_tracker.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.dto.AdvisorResult;
import com.skilltracker.student_skill_tracker.model.SkillData;

@Service
public class AiAdvisorService {

    /**
     * Primary entry: build advice from the latest SkillData snapshot.
     */
    public AdvisorResult advise(SkillData sd) {
        if (sd == null) {
            return AdvisorResult.builder()
                    .headline("No data — refresh your profile")
                    .summary("We don't have a recent snapshot. Click Refresh to fetch LeetCode stats.")
                    .microActions(List.of("Open your dashboard and click Refresh"))
                    .dailyPlan(List.of())
                    .weeklyGoals(List.of())
                    .priorities(List.of())
                    .resources(List.of())
                    .confidence(0.0)
                    .rationale("no-skill-data")
                    .build();
        }

        double ps = safe(sd.getProblemSolvingScore());
        double alg = safe(sd.getAlgorithmsScore());
        double ds = safe(sd.getDataStructuresScore());

        double total = ps + alg + ds;
        double maxTotal = 300.0; // if each is 100-scale, adjust if different
        double signal = Math.min(1.0, total / Math.max(1.0, maxTotal));

        String headline = makeHeadline(ps, alg, ds);
        String summary = String.format(Locale.ENGLISH,
                "Your core scores — ProblemSolving: %.1f, Algorithms: %.1f, DataStructures: %.1f. Total: %.1f",
                ps, alg, ds, total
        );

        // Decide priorities: look for lowest area first, then problem composition
        List<String> priorities = new ArrayList<>();
        if (alg <= ds && alg <= ps) priorities.add("Algorithms");
        if (ds <= alg && ds <= ps) priorities.add("Data Structures");
        if (ps <= alg && ps <= ds) priorities.add("Problem Solving");
        // Add secondary topics
        if (!priorities.contains("Algorithms")) priorities.add("Algorithms");
        if (!priorities.contains("Data Structures")) priorities.add("Data Structures");
        if (!priorities.contains("Problem Solving")) priorities.add("Problem Solving");

        // Micro actions — quick wins based on weak areas
        List<String> micro = new ArrayList<>();
        if (alg < 50) {
            micro.add("Solve 2 algorithm problems (easy) on LeetCode: focus on two-pointer and binary search.");
            micro.add("Read a 10-min article on algorithm complexity (O-notation refresher).");
        } else {
            micro.add("Solve 1 medium algorithm problem; try to optimize from O(n^2) to O(n log n).");
        }

        if (ds < 50) {
            micro.add("Implement a linked list and a stack from scratch in your preferred language (15–20 min).");
        } else {
            micro.add("Revise tree traversals (inorder/preorder/postorder) and solve 1 related problem.");
        }

        // Daily plan
        List<String> daily = new ArrayList<>();
        daily.add("Warmup: 15 min — easy problems (2-3) to build rhythm.");
        if (priorities.get(0).equals("Algorithms")) {
            daily.add("Focused practice: 40 min — Algorithms drills (sorting, greedy, dynamic programming basics).");
        } else if (priorities.get(0).equals("Data Structures")) {
            daily.add("Focused practice: 40 min — Data structures (trees, heaps, hash maps).");
        } else {
            daily.add("Focused practice: 40 min — Mixed problems emphasizing problem solving patterns.");
        }
        daily.add("Debrief: 10 min — write a short note about what you learned today.");

        // Weekly goals
        List<String> weekly = new ArrayList<>();
        weekly.add("Complete 3 medium problems and 8 easy problems this week.");
        weekly.add("Submit one solved problem with a clear explanation to your notes/GitHub.");
        weekly.add("Review mistakes and convert 2 wrong submissions into study flashcards.");

        // Resources (local/quick)
        List<String> resources = List.of(
                "https://leetcode.com/problemset/all/",
                "https://visualgo.net/en",
                "https://cses.fi/book.html (for algorithm practice)"
        );

        double confidence = 0.4 + 0.6 * signal; // more score => higher confidence

        String rationale = buildRationale(sd, ps, alg, ds, total);

        AdvisorResult result = AdvisorResult.builder()
                .headline(headline)
                .summary(summary)
                .microActions(micro)
                .dailyPlan(daily)
                .weeklyGoals(weekly)
                .priorities(priorities)
                .resources(resources)
                .confidence(round(confidence))
                .rationale(rationale)
                .build();

        // LLM hook (optional)
        // String aiText = generateWithLLM(result, sd);
        // result.setSummary(aiText);

        return result;
    }

    private double safe(Double v) {
        return v == null ? 0.0 : v;
    }

    private String makeHeadline(double ps, double alg, double ds) {
        if (alg < 50 && ds < 50 && ps < 50) return "Foundational work needed — start small & be consistent";
        if (alg < 50) return "Algorithms need work — simplify, pattern-match, repeat";
        if (ds < 50) return "Data Structures focus — build from implementation to problems";
        if (ps < 50) return "Problem solving stamina — practice mixing puzzles";
        return "Solid progress — sharpen for medium/hard problems";
    }

    private String buildRationale(SkillData sd, double ps, double alg, double ds, double total) {
        StringBuilder sb = new StringBuilder();
        sb.append("computedScores: ps=").append(ps)
          .append(", alg=").append(alg)
          .append(", ds=").append(ds)
          .append("; problemsSolved=").append(sd.getTotalProblemsSolved())
          .append("; easy=").append(sd.getEasyProblems())
          .append(", medium=").append(sd.getMediumProblems())
          .append(", hard=").append(sd.getHardProblems());
        return sb.toString();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Optional: placeholder for LLM integration. Keep offline until you add API keys.
     * The method would construct a prompt from result + skill snapshot and call an LLM,
     * then post-process to a short summary or expanded plan.
     */
    @SuppressWarnings("unused")
    private String generateWithLLM(AdvisorResult base, SkillData sd) {
        // Build a compact prompt that focuses on actionable steps and study micro-tasks.
        String prompt = "You are a concise coding coach. Student snapshot: " + base.getRationale()
                + "\nReturn a 3-line actionable summary and 3 micro-tasks.";
        // call your preferred LLM SDK here and return the text.
        return base.getSummary(); // fallback
    }
}
