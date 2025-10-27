package com.skilltracker.student_skill_tracker.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LeetCodeService {

    private static final String BASE_URL = "https://leetcode-stats-api.herokuapp.com/";
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> fetchStats(String username) {
        try {
            String url = BASE_URL + username;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch LeetCode data: " + e.getMessage());
            return Map.of(); // fallback empty map
        }
    }
}
