package com.skilltracker.student_skill_tracker.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.skilltracker.student_skill_tracker.dto.AdvisorResult;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;

@Service
public class AiAdvisorService {

    private final CommonQuestionsService commonQuestionsService;
    private final SkillDataRepository skillDataRepository;

    public AiAdvisorService(CommonQuestionsService commonQuestionsService, SkillDataRepository skillDataRepository) {
        this.commonQuestionsService = commonQuestionsService;
        this.skillDataRepository = skillDataRepository;
    }

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
                    .motivationalQuote("Let's get started! Refresh your profile to get your first piece of advice.")
                    .rationale("no-skill-data")
                    .build();
        }

        double ps = safe(sd.getProblemSolvingScore());
        double alg = safe(sd.getAlgorithmsScore());
        double ds = safe(sd.getDataStructuresScore());

        double total = ps + alg + ds;
        double maxTotal = 300.0; // if each is 100-scale, adjust if different
        double signal = Math.min(1.0, total / Math.max(1.0, maxTotal));

        // Decide priorities: look for lowest area first, then problem composition
        List<String> priorities = new ArrayList<>();
        if (alg <= ds && alg <= ps) priorities.add("Algorithms");
        if (ds <= alg && ds <= ps) priorities.add("Data Structures");
        if (ps <= alg && ps <= ds) priorities.add("Problem Solving");
        // Add secondary topics
        if (!priorities.contains("Algorithms")) priorities.add("Algorithms");
        if (!priorities.contains("Data Structures")) priorities.add("Data Structures");
        if (!priorities.contains("Problem Solving")) priorities.add("Problem Solving");

        // --- Question of the Day ---
        String topPriority = priorities.get(0);
        List<Map<String, Object>> commonQuestions = commonQuestionsService.getCommonQuestionsForStudent(sd.getStudent().getId());
        List<Map<String, Object>> personalized = commonQuestionsService.personalizeQuestions(commonQuestions, sd, topPriority);
        Map<String, Object> questionOfTheDay = personalized.isEmpty() ? Collections.emptyMap() : personalized.get(0);

        // --- New Holistic Summary & Headline ---
        String headline = generateDynamicHeadline(topPriority, ps, alg, ds);
        String summary = generateHolisticSummary(sd, topPriority, priorities.get(1), questionOfTheDay);

        // --- Motivational Quote ---
        List<SkillData> history = skillDataRepository.findTop2ByStudentOrderByCreatedAtDesc(sd.getStudent());
        String motivationalQuote = generateMotivationalQuote(history);


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

        // Long-term plan
        List<String> longTerm = generateLongTermPlan(ps, alg, ds);

        // Personalized resources
        List<String> resources = getPersonalizedResources(priorities);

        double confidence = 0.4 + 0.6 * signal; // more score => higher confidence
        String confidenceRationale = getConfidenceRationale(sd, confidence);

        String rationale = buildRationale(sd, ps, alg, ds, total);

        AdvisorResult result = AdvisorResult.builder()
                .headline(headline)
                .summary(summary)
                .microActions(micro)
                .dailyPlan(daily)
                .weeklyGoals(weekly)
                .longTermPlan(longTerm)
                .priorities(priorities)
                .resources(resources)
                .questionOfTheDay(questionOfTheDay)
                .motivationalQuote(motivationalQuote)
                .confidence(round(confidence))
                .confidenceRationale(confidenceRationale)
                .rationale(rationale)
                .build();

        // LLM hook (optional)
        // String aiText = generateWithLLM(result, sd);
        // result.setSummary(aiText);

        return result;
    }

    private String generateDynamicHeadline(String topPriority, double ps, double alg, double ds) {
        if (ps + alg + ds < 10) {
            return "Welcome! Let's Build Your Foundation.";
        }
        switch (topPriority) {
            case "Algorithms":
                return "Focus on Algorithms to Break Through";
            case "Data Structures":
                return "Master Data Structures for a Solid Core";
            case "Problem Solving":
                return "Sharpen Your Problem-Solving Patterns";
            default:
                return "Solid Progress! Time to Optimize.";
        }
    }

    private String generateHolisticSummary(SkillData sd, String topPriority, String secondaryPriority, Map<String, Object> questionOfTheDay) {
        StringBuilder summary = new StringBuilder();
        double topScore = 0;
        double secondaryScore = 0;

        switch (topPriority) {
            case "Algorithms": topScore = safe(sd.getAlgorithmsScore()); break;
            case "Data Structures": topScore = safe(sd.getDataStructuresScore()); break;
            case "Problem Solving": topScore = safe(sd.getProblemSolvingScore()); break;
        }

        switch (secondaryPriority) {
            case "Algorithms": secondaryScore = safe(sd.getAlgorithmsScore()); break;
            case "Data Structures": secondaryScore = safe(sd.getDataStructuresScore()); break;
            case "Problem Solving": secondaryScore = safe(sd.getProblemSolvingScore()); break;
        }

        summary.append(String.format(
            Locale.ENGLISH,
            "Your main focus should be on **%s**, where your score is %.1f. ",
            topPriority,
            topScore
        ));

        summary.append(String.format(
            Locale.ENGLISH,
            "You're showing solid progress in **%s** (%.1f), so let's work on bringing that %s score up! ",
            secondaryPriority,
            secondaryScore,
            topPriority
        ));

        if (questionOfTheDay != null && !questionOfTheDay.isEmpty()) {
            summary.append(String.format(
                "To get you started, I've selected **'%s'** as your Question of the Day. ",
                questionOfTheDay.get("title")
            ));
            if ((Boolean) questionOfTheDay.getOrDefault("isTopTier", false)) {
                summary.append("It's a top-tier problem that will be a great step forward. ");
            }
        }

        summary.append("Let's keep the momentum going!");

        return summary.toString();
    }

    private String generateMotivationalQuote(List<SkillData> history) {
        if (history.size() < 2) {
            return "Every master was once a beginner. Your journey starts now!";
        }
        SkillData latest = history.get(0);
        SkillData previous = history.get(1);

        double latestTotal = safe(latest.getProblemSolvingScore()) + safe(latest.getAlgorithmsScore()) + safe(latest.getDataStructuresScore());
        double previousTotal = safe(previous.getProblemSolvingScore()) + safe(previous.getAlgorithmsScore()) + safe(previous.getDataStructuresScore());

        if (latestTotal > previousTotal) {
            return String.format(Locale.ENGLISH, "Your total score improved by %.1f points! Keep up the great work.", latestTotal - previousTotal);
        }

        long daysBetween = Duration.between(previous.getCreatedAt(), latest.getCreatedAt()).toDays();
        if (daysBetween > 7) {
            return "It's been a little while. Consistency is key—let's solve a problem today!";
        }

        return "The secret to getting ahead is getting started. Let's do this!";
    }

    private List<String> generateLongTermPlan(double ps, double alg, double ds) {
        double total = ps + alg + ds;
        List<String> plan = new ArrayList<>();

        if (total < 100) {
            plan.add("Month 1: Master fundamentals. Complete 50 easy problems. Implement core data structures (Arrays, Strings, Linked Lists, Stacks, Queues) from scratch.");
            plan.add("Month 2: Begin pattern recognition. Focus on Two Pointers, Sliding Window, and basic recursion. Attempt 20 medium problems.");
            plan.add("Month 3: Solidify core algorithms. Deep dive into Sorting (Merge Sort, Quick Sort), Searching (Binary Search), and basic Graph/Tree traversals (BFS, DFS).");
        } else if (total < 200) {
            plan.add("Month 1: Strengthen weak areas. Identify your lowest score and dedicate this month to it. Solve 30 medium problems in that category.");
            plan.add("Month 2: Advanced topics. Introduce yourself to Dynamic Programming, Greedy algorithms, and advanced graph problems. Aim for 15 medium and 5 hard problems.");
            plan.add("Month 3: Interview readiness. Participate in mock interviews and weekly contests. Focus on optimizing solutions and explaining your thought process.");
        } else {
            plan.add("Month 1: Master advanced topics. Deep dive into advanced DP, complex graph algorithms (Dijkstra's, A*), and segment trees.");
            plan.add("Month 2: Competitive programming focus. Regularly participate in contests (LeetCode, Codeforces). Aim for a higher rating and solve hard problems under time constraints.");
            plan.add("Month 3: Specialize and teach. Pick a niche area (e.g., computational geometry, advanced string algorithms) and go deep. Mentor another student or write articles to solidify your understanding.");
        }
        return plan;
    }

    private List<String> getPersonalizedResources(List<String> priorities) {
        List<String> resources = new ArrayList<>();
        if (priorities.isEmpty()) {
            return resources;
        }

        String topPriority = priorities.get(0);

        switch (topPriority) {
            case "Algorithms":
                resources.add("TopCoder Algorithm Tutorials: https://www.topcoder.com/community/data-science/data-science-tutorials/");
                resources.add("Introduction to Algorithms (CLRS): A comprehensive, in-depth textbook.");
                resources.add("CSES Problem Set: https://cses.fi/problemset/");
                break;
            case "Data Structures":
                resources.add("GeeksforGeeks Data Structures: https://www.geeksforgeeks.org/data-structures/");
                resources.add("VisuAlgo: https://visualgo.net/en - for visualizing data structures and algorithms.");
                resources.add("LeetCode's Data Structure Study Plan: https://leetcode.com/study-plan/data-structure/");
                break;
            case "Problem Solving":
                resources.add("HackerRank's Problem Solving Track: https://www.hackerrank.com/domains/algorithms");
                resources.add("Book: Cracking the Coding Interview by Gayle Laakmann McDowell");
                resources.add("Project Euler: https://projecteuler.net/ - for mathematical and computational problems.");
                break;
            default:
                resources.add("LeetCode Problemset: https://leetcode.com/problemset/all/");
                break;
        }
        return resources;
    }

    private String getConfidenceRationale(SkillData sd, double confidence) {
        if (sd == null || sd.getTotalProblemsSolved() == null || sd.getTotalProblemsSolved() == 0) {
            return "Confidence is zero because no data is available.";
        }
        if (sd.getTotalProblemsSolved() < 10) {
            return "Confidence is low as the analysis is based on a very small number of solved problems. Solve more problems to get a more accurate assessment.";
        }
        if (confidence < 0.6) {
            return "Confidence is moderate. While you have a good number of solved problems, your skills may be unbalanced. A more accurate assessment will be possible with more consistent scores.";
        }
        return "Confidence is high. The assessment is based on a solid foundation of solved problems and balanced skills.";
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
