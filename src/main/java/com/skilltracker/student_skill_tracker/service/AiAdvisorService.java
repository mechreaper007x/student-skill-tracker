package com.skilltracker.student_skill_tracker.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilltracker.student_skill_tracker.dto.AdvisorResult;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.repository.SkillDataRepository;

import jakarta.annotation.PostConstruct;

@Service
public class AiAdvisorService {

    private static final Logger logger = LoggerFactory.getLogger(AiAdvisorService.class);

    private final CommonQuestionsService commonQuestionsService;
    private final SkillDataRepository skillDataRepository;
    private final ObjectMapper mapper;

    private Map<String, Object> adviceContent = Collections.emptyMap();

    public AiAdvisorService(CommonQuestionsService commonQuestionsService, 
                           SkillDataRepository skillDataRepository,
                           ObjectMapper mapper) {
        this.commonQuestionsService = commonQuestionsService;
        this.skillDataRepository = skillDataRepository;
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource r = new ClassPathResource("advice_content.json");
            this.adviceContent = mapper.readValue(r.getInputStream(), new TypeReference<Map<String, Object>>() {});
            logger.info("Loaded advice content from resource");
        } catch (IOException e) {
            logger.error("Failed to load advice content resource", e);
        }
    }

    public AdvisorResult advise(SkillData sd) {
        if (sd == null) {
            return buildDefaultResult();
        }

        double ps = safe(sd.getProblemSolvingScore());
        double alg = safe(sd.getAlgorithmsScore());
        double ds = safe(sd.getDataStructuresScore());
        double total = ps + alg + ds;

        List<String> priorities = determinePriorities(ps, alg, ds);
        String topPriority = priorities.get(0);

        List<Map<String, Object>> commonQuestions = commonQuestionsService.getCommonQuestionsForStudent(sd.getStudent().getId());
        List<Map<String, Object>> personalized = commonQuestionsService.personalizeQuestions(commonQuestions, sd, topPriority);
        Map<String, Object> questionOfTheDay = personalized.isEmpty() ? Collections.emptyMap() : personalized.get(0);

        String headline = generateDynamicHeadline(topPriority, ps, alg, ds);
        String summary = generateHolisticSummary(sd, priorities, questionOfTheDay);

        List<SkillData> history = skillDataRepository.findTop2ByStudentOrderByCreatedAtDesc(sd.getStudent());
        String motivationalQuote = generateMotivationalQuote(history);

        double confidence = calculateConfidence(sd, total);

        return AdvisorResult.builder()
                .headline(headline)
                .summary(summary)
                .microActions(generateMicroActions(ps, alg, ds))
                .dailyPlan(generateDailyPlan(topPriority))
                .weeklyGoals(generateWeeklyGoals())
                .longTermPlan(generateLongTermPlan(total))
                .priorities(priorities)
                .resources(getPersonalizedResources(priorities))
                .questionOfTheDay(questionOfTheDay)
                .motivationalQuote(motivationalQuote)
                .confidence(round(confidence))
                .confidenceRationale(getConfidenceRationale(sd, confidence))
                .rationale(buildRationale(sd, ps, alg, ds, total))
                .build();
    }

    private List<String> determinePriorities(double ps, double alg, double ds) {
        List<String> priorities = new ArrayList<>();
        if (alg <= ds && alg <= ps) priorities.add("Algorithms");
        if (ds <= alg && ds <= ps) priorities.add("Data Structures");
        if (ps <= alg && ps <= ds) priorities.add("Problem Solving");
        
        if (!priorities.contains("Algorithms")) priorities.add("Algorithms");
        if (!priorities.contains("Data Structures")) priorities.add("Data Structures");
        if (!priorities.contains("Problem Solving")) priorities.add("Problem Solving");
        return priorities;
    }

    @SuppressWarnings("unchecked")
    private String generateDynamicHeadline(String topPriority, double ps, double alg, double ds) {
        Map<String, String> headlines = (Map<String, String>) adviceContent.get("headlines");
        if (headlines == null) return "Focus on your progress.";

        if (ps + alg + ds < 10) return headlines.getOrDefault("newbie", "Welcome!");
        
        return switch (topPriority) {
            case "Algorithms" -> headlines.getOrDefault("algorithms", "Focus on Algorithms");
            case "Data Structures" -> headlines.getOrDefault("data_structures", "Master Data Structures");
            case "Problem Solving" -> headlines.getOrDefault("problem_solving", "Sharpen Problem Solving");
            default -> headlines.getOrDefault("default", "Time to Optimize");
        };
    }

    private String generateHolisticSummary(SkillData sd, List<String> priorities, Map<String, Object> questionOfTheDay) {
        String topPriority = priorities.get(0);
        String secondaryPriority = priorities.get(1);
        double topScore = getScoreByPriority(sd, topPriority);
        double secondaryScore = getScoreByPriority(sd, secondaryPriority);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format(Locale.ENGLISH, "Your main focus should be on **%s**, where your score is %.1f. ", topPriority, topScore));
        summary.append(String.format(Locale.ENGLISH, "You're showing solid progress in **%s** (%.1f). ", secondaryPriority, secondaryScore));

        if (questionOfTheDay != null && !questionOfTheDay.isEmpty()) {
            summary.append(String.format("To get you started, try **'%s'** today. ", questionOfTheDay.get("title")));
        }
        summary.append("Keep pushing!");
        return summary.toString();
    }

    private double getScoreByPriority(SkillData sd, String priority) {
        return switch (priority) {
            case "Algorithms" -> safe(sd.getAlgorithmsScore());
            case "Data Structures" -> safe(sd.getDataStructuresScore());
            case "Problem Solving" -> safe(sd.getProblemSolvingScore());
            default -> 0.0;
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> generateMicroActions(double ps, double alg, double ds) {
        Map<String, List<String>> actions = (Map<String, List<String>>) adviceContent.get("microActions");
        List<String> result = new ArrayList<>();
        if (actions == null) return result;

        if (alg < 50) result.addAll(actions.getOrDefault("algorithms_low", Collections.emptyList()));
        else result.addAll(actions.getOrDefault("algorithms_high", Collections.emptyList()));

        if (ds < 50) result.addAll(actions.getOrDefault("ds_low", Collections.emptyList()));
        else result.addAll(actions.getOrDefault("ds_high", Collections.emptyList()));

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> generateLongTermPlan(double total) {
        Map<String, List<String>> plans = (Map<String, List<String>>) adviceContent.get("longTermPlans");
        if (plans == null) return Collections.emptyList();

        if (total < 100) return plans.getOrDefault("beginner", Collections.emptyList());
        if (total < 200) return plans.getOrDefault("intermediate", Collections.emptyList());
        return plans.getOrDefault("advanced", Collections.emptyList());
    }

    private List<String> generateDailyPlan(String topPriority) {
        return List.of(
            "Warmup: 15 min — easy problems (2-3) to build rhythm.",
            String.format("Focused practice: 40 min — %s drills and related problems.", topPriority),
            "Debrief: 10 min — write a short note about what you learned today."
        );
    }

    private List<String> generateWeeklyGoals() {
        return List.of(
            "Complete 3 medium problems and 8 easy problems this week.",
            "Submit one solved problem with a clear explanation to your notes/GitHub.",
            "Review mistakes and convert 2 wrong submissions into study flashcards."
        );
    }

    private List<String> getPersonalizedResources(List<String> priorities) {
        List<String> resources = new ArrayList<>();
        if (priorities.isEmpty()) return resources;

        switch (priorities.get(0)) {
            case "Algorithms" -> {
                resources.add("TopCoder Algorithm Tutorials: https://www.topcoder.com/community/data-science/data-science-tutorials/");
                resources.add("Introduction to Algorithms (CLRS): A comprehensive, in-depth textbook.");
            }
            case "Data Structures" -> {
                resources.add("GeeksforGeeks Data Structures: https://www.geeksforgeeks.org/data-structures/");
                resources.add("VisuAlgo: https://visualgo.net/en");
            }
            case "Problem Solving" -> {
                resources.add("HackerRank's Problem Solving Track: https://www.hackerrank.com/domains/algorithms");
                resources.add("Book: Cracking the Coding Interview");
            }
        }
        return resources;
    }

    private String generateMotivationalQuote(List<SkillData> history) {
        if (history.size() < 2) return "Every master was once a beginner. Your journey starts now!";
        SkillData latest = history.get(0);
        SkillData previous = history.get(1);

        double latestTotal = safe(latest.getProblemSolvingScore()) + safe(latest.getAlgorithmsScore()) + safe(latest.getDataStructuresScore());
        double previousTotal = safe(previous.getProblemSolvingScore()) + safe(previous.getAlgorithmsScore()) + safe(previous.getDataStructuresScore());

        if (latestTotal > previousTotal) {
            return String.format(Locale.ENGLISH, "Your total score improved by %.1f points! Keep it up.", latestTotal - previousTotal);
        }
        return "The secret to getting ahead is getting started. Let's do this!";
    }

    private double calculateConfidence(SkillData sd, double totalScore) {
        double maxTotal = 300.0;
        double signal = Math.min(1.0, totalScore / Math.max(1.0, maxTotal));
        return 0.4 + 0.6 * signal;
    }

    private String getConfidenceRationale(SkillData sd, double confidence) {
        if (sd == null || sd.getTotalProblemsSolved() == null || sd.getTotalProblemsSolved() == 0) return "Confidence is zero because no data is available.";
        if (sd.getTotalProblemsSolved() < 10) return "Confidence is low due to limited data.";
        return confidence < 0.6 ? "Confidence is moderate." : "Confidence is high.";
    }

    private AdvisorResult buildDefaultResult() {
        return AdvisorResult.builder()
                .headline("No data — refresh your profile")
                .summary("Click Refresh to fetch LeetCode stats.")
                .microActions(List.of("Open your dashboard and click Refresh"))
                .motivationalQuote("Let's get started!")
                .confidence(0.0)
                .build();
    }

    private double safe(Double v) { return v == null ? 0.0 : v; }
    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
    private String buildRationale(SkillData sd, double ps, double alg, double ds, double total) {
        return String.format(Locale.ENGLISH, "ps=%.1f, alg=%.1f, ds=%.1f; totalSolved=%d", ps, alg, ds, sd.getTotalProblemsSolved());
    }
}