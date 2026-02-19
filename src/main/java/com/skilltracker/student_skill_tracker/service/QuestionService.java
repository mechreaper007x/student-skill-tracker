package com.skilltracker.student_skill_tracker.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class QuestionService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionService.class);
    private static final Pattern LEETCODE_PROBLEM_SLUG_PATTERN = Pattern
            .compile("leetcode\\.com/problems/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private List<Map<String, Object>> commonQuestions = new ArrayList<>();
    private List<Map<String, Object>> topTierQuestions = new ArrayList<>();
    private List<Map<String, Object>> trendingQuestions = new ArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        commonQuestions = normalizeAndDeduplicate(loadQuestions("common_questions.json"));
        topTierQuestions = normalizeAndDeduplicate(loadQuestions("top_tier_questions.json"));
        trendingQuestions = normalizeAndDeduplicate(loadQuestions("trending_questions.json"));

        logger.info("Loaded questions — Common: {}, Top-Tier: {}, Trending: {}",
                commonQuestions.size(), topTierQuestions.size(), trendingQuestions.size());
    }

    private List<Map<String, Object>> loadQuestions(String filename) {
        try {
            InputStream is = new ClassPathResource(filename).getInputStream();
            return objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (IOException e) {
            logger.error("Failed to load {}: {}", filename, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Normalize the raw question shape and deduplicate by canonical slug.
     * Some data sources contain synthetic variants like /problem-slug/remix which
     * are not real LeetCode pages and cause 404. We canonicalize each URL to
     * /problems/{slug}/.
     */
    private List<Map<String, Object>> normalizeAndDeduplicate(List<Map<String, Object>> questions) {
        Map<String, Map<String, Object>> bySlug = new LinkedHashMap<>();

        for (Map<String, Object> question : questions) {
            Map<String, Object> normalized = normalizeQuestion(question);
            String slug = asString(normalized.get("slug"));
            if (slug == null || slug.isBlank()) {
                continue;
            }

            bySlug.merge(slug, normalized, this::pickBetterVariant);
        }

        return new ArrayList<>(bySlug.values());
    }

    private Map<String, Object> normalizeQuestion(Map<String, Object> rawQuestion) {
        Map<String, Object> normalized = new HashMap<>();

        String title = sanitizeTitle(asString(rawQuestion.get("title")));
        String difficulty = normalizeDifficulty(asString(rawQuestion.get("difficulty")));
        String slug = extractProblemSlug(asString(rawQuestion.get("url")));
        if (slug == null || slug.isBlank()) {
            slug = slugify(title);
        }

        if (slug == null || slug.isBlank()) {
            return Map.of();
        }

        normalized.put("title", title == null || title.isBlank() ? humanizeSlug(slug) : title);
        normalized.put("difficulty", difficulty);
        normalized.put("tags", normalizeTags(rawQuestion.get("tags")));
        normalized.put("slug", slug);
        normalized.put("url", buildCanonicalUrl(slug));
        return normalized;
    }

    private Map<String, Object> pickBetterVariant(Map<String, Object> existing, Map<String, Object> candidate) {
        int existingScore = questionQualityScore(existing);
        int candidateScore = questionQualityScore(candidate);

        if (candidateScore > existingScore) {
            return candidate;
        }
        return existing;
    }

    private int questionQualityScore(Map<String, Object> question) {
        String title = asString(question.get("title"));
        String difficulty = asString(question.get("difficulty"));
        int tags = normalizeTags(question.get("tags")).size();

        int score = 0;
        if (title != null) {
            String lower = title.toLowerCase(Locale.ROOT);
            if (!lower.contains("remix") && !lower.contains("ultimate") && !lower.contains("extreme")) {
                score += 4;
            }
            if (title.length() <= 50) {
                score += 2;
            }
        }

        if (difficulty != null && !"Unknown".equalsIgnoreCase(difficulty)) {
            score += 2;
        }
        if (tags > 0) {
            score += 1;
        }
        return score;
    }

    private String extractProblemSlug(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }

        Matcher matcher = LEETCODE_PROBLEM_SLUG_PATTERN.matcher(url);
        if (!matcher.find()) {
            return "";
        }
        return sanitizeSlug(matcher.group(1));
    }

    private String sanitizeSlug(String slug) {
        if (slug == null) {
            return "";
        }
        return slug
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private String buildCanonicalUrl(String slug) {
        return "https://leetcode.com/problems/" + slug + "/";
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "Unknown";
        }
        String normalized = difficulty.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "easy" -> "Easy";
            case "medium" -> "Medium";
            case "hard" -> "Hard";
            default -> "Unknown";
        };
    }

    private String sanitizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim().replaceAll("\\s{2,}", " ");
    }

    private List<String> normalizeTags(Object tagsValue) {
        if (!(tagsValue instanceof List<?> rawList)) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (Object tag : rawList) {
            if (tag == null) {
                continue;
            }
            String str = String.valueOf(tag).trim();
            if (!str.isBlank()) {
                normalized.add(str);
            }
        }
        return normalized;
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return sanitizeSlug(input.replaceAll("[^a-zA-Z0-9]+", "-"));
    }

    private String humanizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "Untitled Problem";
        }
        return List.of(slug.split("-")).stream()
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // --- Getters ---

    public List<Map<String, Object>> getCommonQuestions() {
        return commonQuestions;
    }

    public List<Map<String, Object>> getTopTierQuestions() {
        return topTierQuestions;
    }

    public List<Map<String, Object>> getTrendingQuestions() {
        return trendingQuestions;
    }

    /**
     * Get all questions from all categories, deduplicated.
     */
    public List<Map<String, Object>> getAllQuestions() {
        List<Map<String, Object>> all = new ArrayList<>();
        all.addAll(commonQuestions);
        all.addAll(topTierQuestions);
        all.addAll(trendingQuestions);
        return normalizeAndDeduplicate(all);
    }

    /**
     * Filter by difficulty across all categories.
     */
    public List<Map<String, Object>> getByDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return List.of();
        }
        String normalizedDifficulty = normalizeDifficulty(difficulty);
        return getAllQuestions().stream()
                .filter(q -> normalizedDifficulty.equalsIgnoreCase(asString(q.get("difficulty"))))
                .collect(Collectors.toList());
    }

    /**
     * Filter by tag across all categories.
     */
    public List<Map<String, Object>> getByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        String targetTag = tag.trim().toLowerCase(Locale.ROOT);
        return getAllQuestions().stream()
                .filter(q -> {
                    return normalizeTags(q.get("tags")).stream()
                            .map(entry -> entry.toLowerCase(Locale.ROOT))
                            .anyMatch(entry -> Objects.equals(entry, targetTag));
                })
                .collect(Collectors.toList());
    }

    /**
     * Get a random daily challenge (Medium or Hard from trending).
     */
    public Map<String, Object> getDailyChallenge() {
        List<Map<String, Object>> eligible = trendingQuestions.stream()
                .filter(q -> {
                    String diff = asString(q.get("difficulty"));
                    return "Medium".equalsIgnoreCase(diff) || "Hard".equalsIgnoreCase(diff);
                })
                .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            return Map.of("error", "No eligible questions found");
        }

        // Use day-of-year as seed for deterministic daily selection
        int dayOfYear = java.time.LocalDate.now().getDayOfYear();
        return eligible.get(dayOfYear % eligible.size());
    }
}
