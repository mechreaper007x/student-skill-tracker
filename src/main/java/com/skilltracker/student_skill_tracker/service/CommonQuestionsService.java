package com.skilltracker.student_skill_tracker.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skilltracker.student_skill_tracker.model.SkillData;

import jakarta.annotation.PostConstruct;

@Service
public class CommonQuestionsService {

    private static final Logger logger = LoggerFactory.getLogger(CommonQuestionsService.class);

    private final ObjectMapper mapper;

    private List<Map<String, Object>> questions = Collections.emptyList();
    private List<Map<String, Object>> trendingQuestions = Collections.emptyList();

    public CommonQuestionsService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource r = new ClassPathResource("common_questions.json");
            this.questions = mapper.readValue(r.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
            logger.info("Loaded {} common questions from resource", questions.size());
        } catch (IOException e) {
            logger.warn("Failed to load common questions resource, continuing with empty list", e);
            this.questions = Collections.emptyList();
        }
        try {
            ClassPathResource r2 = new ClassPathResource("trending_questions.json");
            this.trendingQuestions = mapper.readValue(r2.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
            logger.info("Loaded {} trending questions from resource", trendingQuestions.size());
        } catch (IOException e) {
            logger.warn("Failed to load trending questions resource, continuing with empty list", e);
            this.trendingQuestions = Collections.emptyList();
        }
    }

    /**
     * Return curated list of common questions. For now this is global and not personalized.
     */
    public List<Map<String, Object>> getCommonQuestionsForStudent(Long studentId) {
        return questions;
    }

    public List<Map<String, Object>> getTrendingQuestionsForStudent(Long studentId) {
        return trendingQuestions;
    }

    /**
     * Return a personalized ordering of the provided questions based on the student's weak areas.
     * Current strategy:
     * - Determine weak skill areas by score thresholds
     * - Map skill areas to likely tags and boost questions that contain those tags
     * - Sort by number of matched weak-tags (desc) then by difficulty (Easy &lt; Medium &lt; Hard)
     */
    public List<Map<String, Object>> personalizeQuestions(List<Map<String, Object>> inputQuestions, SkillData skillData, String topPriority) {
        if (inputQuestions == null) return Collections.emptyList();
        if (skillData == null) return new ArrayList<>(inputQuestions);

        // Determine weak areas
        Set<String> weakTags = new HashSet<>();

        Double algo = skillData.getAlgorithmsScore();
        Double ds = skillData.getDataStructuresScore();
        Double ps = skillData.getProblemSolvingScore();

        // Thresholds: consider < 60% as weak
        if (algo == null || algo < 60.0) {
            Collections.addAll(weakTags, "algorithms", "binary-search", "dynamic-programming", "greedy", "divide-and-conquer", "two-pointers");
        }
        if (ds == null || ds < 60.0) {
            Collections.addAll(weakTags, "data-structures", "linked-list", "tree", "heap", "stack", "queue", "hash-table");
        }
        if (ps == null || ps < 60.0) {
            Collections.addAll(weakTags, "array", "string", "sliding-window", "two-pointers", "math", "hash-table");
        }

        // Build a working list of entries with score and matched tags
        class Entry {
            Map<String, Object> q;
            int score;
            List<String> matchedTags;
            Entry(Map<String, Object> q, int score, List<String> matchedTags) { this.q = q; this.score = score; this.matchedTags = matchedTags; }
        }

        List<Entry> entries = new ArrayList<>();
        for (Map<String, Object> q : inputQuestions) {
            List<String> matched = computeMatchedTags(q, weakTags);
            int sc = 0;
            for (String tag : matched) {
                if (topPriority != null && topPriority.toLowerCase().contains(tag)) { // Simple check for priority match
                    sc += 3; // Higher weight for top priority
                } else {
                    sc += 1;
                }
            }

            // Adaptive Difficulty: Penalize harder questions if scores are low
            String difficulty = (String) q.get("difficulty");
            if ("Medium".equalsIgnoreCase(difficulty)) {
                if ((algo != null && algo < 40) || (ds != null && ds < 40) || (ps != null && ps < 40)) {
                    sc -= 10; // Heavily penalize Medium questions if any score is very low
                }
            } else if ("Hard".equalsIgnoreCase(difficulty)) {
                if ((algo != null && algo < 75) || (ds != null && ds < 75) || (ps != null && ps < 75)) {
                    sc -= 20; // Heavily penalize Hard questions if scores are not high
                }
            }

            entries.add(new Entry(q, sc, matched));
        }

        // Sort: more matched tags first, then easier difficulty
        entries.sort((a,b) -> {
            if (a.score != b.score) return Integer.compare(b.score, a.score);
            int da = difficultyRank((String) a.q.get("difficulty"));
            int db = difficultyRank((String) b.q.get("difficulty"));
            return Integer.compare(da, db);
        });

        // Fallback logic: if no questions matched weak areas, recommend the 3 easiest
        if (entries.isEmpty() || entries.get(0).score == 0) {
            entries.sort((a,b) -> {
                int da = difficultyRank((String) a.q.get("difficulty"));
                int db = difficultyRank((String) b.q.get("difficulty"));
                return Integer.compare(da, db);
            });
        }

        // Build return list, annotate each map copy with matchedTags and recommended flag for top N
        List<Map<String, Object>> out = new ArrayList<>();
        int topN = Math.min(3, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            // create a shallow copy to avoid mutating original resource maps
            Map<String, Object> copy = new java.util.HashMap<>(e.q);
            copy.put("matchedTags", e.matchedTags);
            copy.put("recommendationScore", e.score);
            if (i < topN && (e.score > 0 || entries.get(0).score == 0)) { // Recommend if score > 0 or if fallback is active
                copy.put("recommended", true);
            } else {
                copy.put("recommended", false);
            }
            out.add(copy);
        }

        return out;
    }

    private List<String> computeMatchedTags(Map<String, Object> q, Set<String> weakTags) {
        List<String> matched = new ArrayList<>();
        if (q == null || weakTags == null || weakTags.isEmpty()) return matched;
        Object tagsObj = q.get("tags");
        if (tagsObj == null) return matched;
        try {
            Iterable<?> tags = (Iterable<?>) tagsObj;
            for (Object t : tags) {
                if (t == null) continue;
                String s = t.toString().toLowerCase();
                if (weakTags.contains(s)) matched.add(s);
            }
        } catch (ClassCastException ex) {
            // ignore unexpected format
        }
        return matched;
    }

    private int difficultyRank(String difficulty) {
        if (difficulty == null) return 2; // treat unknown as medium
        String d = difficulty.toLowerCase();
        if (d.contains("easy")) return 1;
        if (d.contains("medium")) return 2;
        if (d.contains("hard")) return 3;
        return 2;
    }
}
