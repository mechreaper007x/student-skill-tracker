package com.skilltracker.student_skill_tracker.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LeetCodeService {

    private static final String BASE_URL = "https://leetcode.com/graphql/";
    private static final int RECENT_ACCEPTED_LIMIT = 200;
    private final RestTemplate restTemplate;

    public LeetCodeService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> fetchStats(String username) {
        // This method might need to be updated to use the GraphQL API as well
        // For now, we leave it as is to avoid breaking other parts of the application
        try {
            String url = "https://leetcode-stats-api.herokuapp.com/" + username;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch LeetCode data: " + e.getMessage());
            return Map.of(); // fallback empty map
        }
    }

    public Map<String, Object> fetchLanguageStats(String username) {
        try {
            String query = """
                    query languageStats($username: String!) {
                        matchedUser(username: $username) {
                            languageProblemCount {
                                languageName
                                problemsSolved
                            }
                        }
                    }
                    """;

            Map<String, Object> variables = Map.of("username", username);
            Map<String, Object> body = Map.of("query", query, "variables", variables);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createHeaders());

            return restTemplate.postForObject(BASE_URL, entity, Map.class);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch LeetCode language data: " + e.getMessage());
            return Map.of();
        }
    }

    public Map<String, Object> fetchFullStats(String username) {
        try {
            String query = """
                    query userProblemsSolved($username: String!, $recentLimit: Int) {
                        allQuestionsCount {
                            difficulty
                            count
                        }
                        topKnowledgeTags {
                            name
                            slug
                            questionCount
                        }
                        recentAcSubmissionList(username: $username, limit: $recentLimit) {
                            title
                            titleSlug
                            timestamp
                            statusDisplay
                            lang
                            langName
                        }
                        matchedUser(username: $username) {
                            submitStats {
                                acSubmissionNum {
                                    difficulty
                                    count
                                    submissions
                                }
                            }
                            profile {
                                ranking
                                reputation
                            }
                            tagProblemCounts {
                                fundamental {
                                    tagName
                                    tagSlug
                                    problemsSolved
                                }
                                intermediate {
                                    tagName
                                    tagSlug
                                    problemsSolved
                                }
                                advanced {
                                    tagName
                                    tagSlug
                                    problemsSolved
                                }
                            }
                        }
                    }
                    """;

            Map<String, Object> variables = Map.of(
                    "username", username,
                    "recentLimit", RECENT_ACCEPTED_LIMIT);
            Map<String, Object> body = Map.of("query", query, "variables", variables);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createHeaders());

            Map<String, Object> response = restTemplate.postForObject(BASE_URL, entity, Map.class);

            Map<String, Object> result = new HashMap<>();

            if (response != null && response.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                Map<String, Object> matchedUser = (Map<String, Object>) data.get("matchedUser");
                List<Map<String, Object>> topKnowledgeTags = asMapList(data.get("topKnowledgeTags"));

                Map<String, Integer> totalQuestionsByTagSlug = new HashMap<>();
                for (Map<String, Object> tag : topKnowledgeTags) {
                    String slug = asString(tag.get("slug"));
                    if (slug == null || slug.isBlank()) {
                        continue;
                    }
                    totalQuestionsByTagSlug.put(slug, asInt(tag.get("questionCount")));
                }

                List<Map<String, Object>> algorithmMastery = new ArrayList<>();

                if (matchedUser != null) {
                    Map<String, Object> submitStats = (Map<String, Object>) matchedUser.get("submitStats");
                    List<Map<String, Object>> acSubmissionNum = submitStats == null ? List.of()
                            : asMapList(submitStats.get("acSubmissionNum"));

                    for (Map<String, Object> item : acSubmissionNum) {
                        String diff = (String) item.get("difficulty");
                        int count = asInt(item.get("count"));
                        if (diff == null || diff.isBlank()) {
                            continue;
                        }
                        result.put(diff.toLowerCase() + "Solved", count);
                        if ("all".equalsIgnoreCase(diff)) {
                            result.put("totalSolved", count);
                        }
                    }

                    Map<String, Object> profile = (Map<String, Object>) matchedUser.get("profile");
                    if (profile != null) {
                        result.put("ranking", profile.get("ranking"));
                        result.put("reputation", profile.get("reputation"));
                    }

                    Map<String, Object> tagProblemCounts = (Map<String, Object>) matchedUser.get("tagProblemCounts");
                    algorithmMastery.addAll(buildAlgorithmMasteryForLevel("Fundamental", tagProblemCounts, "fundamental",
                            totalQuestionsByTagSlug));
                    algorithmMastery.addAll(buildAlgorithmMasteryForLevel("Intermediate", tagProblemCounts, "intermediate",
                            totalQuestionsByTagSlug));
                    algorithmMastery.addAll(buildAlgorithmMasteryForLevel("Advanced", tagProblemCounts, "advanced",
                            totalQuestionsByTagSlug));
                }

                algorithmMastery.sort((left, right) -> {
                    int solvedCompare = Integer.compare(asInt(right.get("problemsSolved")), asInt(left.get("problemsSolved")));
                    if (solvedCompare != 0) {
                        return solvedCompare;
                    }
                    return Double.compare(asDouble(right.get("masteryPercent")), asDouble(left.get("masteryPercent")));
                });
                result.put("algorithmMastery", algorithmMastery);

                List<Map<String, Object>> recentAcSubmissions = asMapList(data.get("recentAcSubmissionList"));
                result.put("recentAcSubmissions", recentAcSubmissions);
            }

            return result;

        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch full LeetCode stats: " + e.getMessage());
            return Map.of();
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        return headers;
    }

    private List<Map<String, Object>> buildAlgorithmMasteryForLevel(
            String levelName,
            Map<String, Object> tagProblemCounts,
            String key,
            Map<String, Integer> totalQuestionsByTagSlug) {
        if (tagProblemCounts == null) {
            return List.of();
        }

        List<Map<String, Object>> source = asMapList(tagProblemCounts.get(key));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> tag : source) {
            String tagSlug = asString(tag.get("tagSlug"));
            String tagName = asString(tag.get("tagName"));
            int solved = asInt(tag.get("problemsSolved"));
            int totalQuestions = tagSlug == null ? 0 : totalQuestionsByTagSlug.getOrDefault(tagSlug, 0);
            double masteryPercent = totalQuestions > 0 ? round2((solved * 100.0) / totalQuestions) : 0.0;

            Map<String, Object> row = new HashMap<>();
            row.put("tagName", tagName == null ? "" : tagName);
            row.put("tagSlug", tagSlug == null ? "" : tagSlug);
            row.put("level", levelName);
            row.put("problemsSolved", solved);
            row.put("totalQuestions", totalQuestions);
            row.put("masteryPercent", masteryPercent);
            row.put("masteryBand", toMasteryBand(masteryPercent, solved));
            result.add(row);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object obj) {
        if (!(obj instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String toMasteryBand(double masteryPercent, int solved) {
        if (masteryPercent >= 50.0) {
            return "Elite";
        }
        if (masteryPercent >= 25.0) {
            return "Strong";
        }
        if (masteryPercent >= 10.0) {
            return "Building";
        }
        if (solved > 0) {
            return "Started";
        }
        return "Unstarted";
    }
}
