package com.skilltracker.student_skill_tracker.service;

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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

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

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            return restTemplate.postForObject(BASE_URL, entity, Map.class);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch LeetCode language data: " + e.getMessage());
            return Map.of();
        }
    }

    public Map<String, Object> fetchFullStats(String username) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            // Query for total solved, breakdown, and acceptance rate
            String query = """
                    query userProblemsSolved($username: String!) {
                        allQuestionsCount {
                            difficulty
                            count
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
                        }
                    }
                    """;

            Map<String, Object> variables = Map.of("username", username);
            Map<String, Object> body = Map.of("query", query, "variables", variables);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            Map<String, Object> response = restTemplate.postForObject(BASE_URL, entity, Map.class);

            // Transform response to a flatter map for easier frontend consumption
            // Start with a basic map
            java.util.Map<String, Object> result = new java.util.HashMap<>();

            if (response != null && response.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                Map<String, Object> matchedUser = (Map<String, Object>) data.get("matchedUser");

                if (matchedUser != null) {
                    Map<String, Object> submitStats = (Map<String, Object>) matchedUser.get("submitStats");
                    List<Map<String, Object>> acSubmissionNum = (List<Map<String, Object>>) submitStats
                            .get("acSubmissionNum");

                    for (Map<String, Object> item : acSubmissionNum) {
                        String diff = (String) item.get("difficulty");
                        Integer count = (Integer) item.get("count");
                        result.put(diff.toLowerCase() + "Solved", count);
                    }

                    Map<String, Object> profile = (Map<String, Object>) matchedUser.get("profile");
                    if (profile != null) {
                        result.put("ranking", profile.get("ranking"));
                        result.put("reputation", profile.get("reputation"));
                    }
                }
            }

            return result;

        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch full LeetCode stats: " + e.getMessage());
            return Map.of();
        }
    }
}
