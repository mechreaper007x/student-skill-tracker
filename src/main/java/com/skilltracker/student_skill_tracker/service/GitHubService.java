package com.skilltracker.student_skill_tracker.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestTemplate restTemplate;

    public GitHubService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch public repositories for a GitHub user.
     * Uses provided token or unauthenticated (60 requests/hour limit).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchRepos(String githubUsername, String token) {
        try {
            String url = GITHUB_API_BASE + "/users/" + githubUsername + "/repos?sort=updated&per_page=20&type=owner";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "StudentSkillTracker/1.0");

            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", "Bearer " + token);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, List.class);

            List<Map<String, Object>> rawRepos = response.getBody();
            if (rawRepos == null) {
                return List.of();
            }

            List<Map<String, Object>> repos = new ArrayList<>();
            for (Map<String, Object> raw : rawRepos) {
                // Skip forked repos (only show original work)
                Boolean fork = (Boolean) raw.get("fork");
                if (Boolean.TRUE.equals(fork))
                    continue;

                Map<String, Object> repo = new HashMap<>();
                repo.put("name", raw.get("name"));
                repo.put("language", raw.getOrDefault("language", "Unknown"));
                repo.put("stars", raw.getOrDefault("stargazers_count", 0));
                repo.put("forks", raw.getOrDefault("forks_count", 0));
                repo.put("description", raw.getOrDefault("description", "No description"));
                repo.put("lastForged", raw.get("updated_at")); // Frontend expects lastForged
                repo.put("url", raw.get("html_url")); // Frontend expects url
                repos.add(repo);
            }

            logger.info("Fetched {} repos for GitHub user: {}", repos.size(), githubUsername);
            return repos;

        } catch (Exception e) {
            logger.error("Failed to fetch GitHub repos for {}: {}", githubUsername, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch language breakdown for a specific repository.
     */
    public Map<String, Long> fetchRepoLanguages(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/languages";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return (Map<String, Long>) response.getBody();
        } catch (Exception e) {
            logger.error("Failed to fetch languages for {}/{}: {}", owner, repo, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Fetch repository file tree (skeleton).
     * Uses the recursive tree API to get the structure.
     */
    public List<Map<String, Object>> fetchRepoSkeleton(String owner, String repo, String token) {
        try {
            // Get default branch SHA first (simplified: assuming 'main' or 'master', but
            // better to query repo info first)
            // For now, let's try to fetch the tree from the default branch using the
            // 'trees' API with recursive=1
            // A more robust way is getting /repos/{owner}/{repo} -> default_branch ->
            // /git/trees/{sha}?recursive=1

            String repoInfoUrl = GITHUB_API_BASE + "/repos/" + owner + "/" + repo;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> repoInfoResponse = restTemplate.exchange(repoInfoUrl, HttpMethod.GET, entity,
                    Map.class);
            String defaultBranch = (String) repoInfoResponse.getBody().get("default_branch");

            String treeUrl = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/git/trees/" + defaultBranch
                    + "?recursive=1";
            ResponseEntity<Map> treeResponse = restTemplate.exchange(treeUrl, HttpMethod.GET, entity, Map.class);

            List<Map<String, Object>> tree = (List<Map<String, Object>>) treeResponse.getBody().get("tree");

            // Filter and shape the data for frontend (limit to 50 items to prevent massive
            // payloads)
            List<Map<String, Object>> skeleton = new ArrayList<>();
            int count = 0;
            if (tree != null) {
                for (Map<String, Object> item : tree) {
                    if (count++ >= 100)
                        break; // Limit to 100 items
                    Map<String, Object> node = new HashMap<>();
                    node.put("path", item.get("path"));
                    node.put("type", item.get("type")); // blob or tree
                    skeleton.add(node);
                }
            }
            return skeleton;

        } catch (Exception e) {
            logger.error("Failed to fetch skeleton for {}/{}: {}", owner, repo, e.getMessage());
            return List.of();
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "StudentSkillTracker/1.0");
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }
}
