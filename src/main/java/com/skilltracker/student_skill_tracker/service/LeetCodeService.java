package com.skilltracker.student_skill_tracker.service;

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

            String query = "query recentSubmissionsList($username: String!, $limit: Int!) { recentSubmissionList(username: $username, limit: $limit) { title titleSlug timestamp statusDisplay lang __typename } }";
            Map<String, Object> variables = Map.of("username", username, "limit", 20);
            Map<String, Object> body = Map.of("query", query, "variables", variables);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            return restTemplate.postForObject(BASE_URL, entity, Map.class);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch LeetCode language data: " + e.getMessage());
            return Map.of(); // fallback empty map
        }
    }
}
