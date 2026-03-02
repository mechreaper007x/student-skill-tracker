package com.skilltracker.student_skill_tracker.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class LeetCodeService {

    private static final Logger logger = LoggerFactory.getLogger(LeetCodeService.class);

    private static final String BASE_URL = "https://leetcode.com/graphql/";
    private static final String SUBMIT_URL_TEMPLATE = "https://leetcode.com/problems/%s/submit/";
    private static final String SUBMISSION_CHECK_URL_TEMPLATE = "https://leetcode.com/submissions/detail/%s/check/";
    private static final int RECENT_ACCEPTED_LIMIT = 200;
    private static final int PROBLEMSET_QUERY_LIMIT = 10;
    private static final int MAX_SUBMISSION_POLL_ATTEMPTS = 10;
    private static final long SUBMISSION_POLL_INTERVAL_MS = 1200L;
    private static final int GRAPHQL_RETRY_ATTEMPTS = 2;
    private static final long GRAPHQL_RETRY_DELAY_MS = 250L;

    private final RestTemplate restTemplate;
    private final CacheManager cacheManager;

    public LeetCodeService(RestTemplate restTemplate, CacheManager cacheManager) {
        this.restTemplate = restTemplate;
        this.cacheManager = cacheManager;
    }

    @Cacheable(value = "leetcodeStats", key = "#username", unless = "#result.isEmpty()")
    public Map<String, Object> fetchStats(String username) {
        try {
            String safeUsername = sanitizeForUrl(username);
            if (safeUsername.isBlank())
                return Map.of();
            String url = "https://leetcode-stats-api.herokuapp.com/" + safeUsername;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            System.err.println("⚠️  Failed to fetch LeetCode data: " + e.getMessage());
            return Map.of();
        }
    }

    private String sanitizeForUrl(String input) {
        if (input == null)
            return "";
        return input.replaceAll("[^a-zA-Z0-9\\-_\\.]", "");
    }

    @Cacheable(value = "leetcodeLanguageStats", key = "#username", unless = "#result.isEmpty()")
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

    @Cacheable(value = "leetcodeFullStats", key = "#username", unless = "#result.isEmpty()")
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
                Map<String, Object> data = asMap(response.get("data"));
                Map<String, Object> matchedUser = asMap(data.get("matchedUser"));
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
                    Map<String, Object> submitStats = asMap(matchedUser.get("submitStats"));
                    List<Map<String, Object>> acSubmissionNum = submitStats == null ? List.of()
                            : asMapList(submitStats.get("acSubmissionNum"));

                    for (Map<String, Object> item : acSubmissionNum) {
                        String diff = asString(item.get("difficulty"));
                        int count = asInt(item.get("count"));
                        if (diff == null || diff.isBlank()) {
                            continue;
                        }
                        result.put(diff.toLowerCase() + "Solved", count);
                        if ("all".equalsIgnoreCase(diff)) {
                            result.put("totalSolved", count);
                        }
                    }

                    Map<String, Object> profile = asMap(matchedUser.get("profile"));
                    if (profile != null) {
                        result.put("ranking", profile.get("ranking"));
                        result.put("reputation", profile.get("reputation"));
                    }

                    Map<String, Object> tagProblemCounts = asMap(matchedUser.get("tagProblemCounts"));
                    algorithmMastery
                            .addAll(buildAlgorithmMasteryForLevel("Fundamental", tagProblemCounts, "fundamental",
                                    totalQuestionsByTagSlug));
                    algorithmMastery
                            .addAll(buildAlgorithmMasteryForLevel("Intermediate", tagProblemCounts, "intermediate",
                                    totalQuestionsByTagSlug));
                    algorithmMastery.addAll(buildAlgorithmMasteryForLevel("Advanced", tagProblemCounts, "advanced",
                            totalQuestionsByTagSlug));
                }

                algorithmMastery.sort((left, right) -> {
                    int solvedCompare = Integer.compare(asInt(right.get("problemsSolved")),
                            asInt(left.get("problemsSolved")));
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

    public ResponseEntity<?> fetchQuestionDetails(String titleSlug, String titleHint, String urlHint) {
        String normalizedSlug = normalizeSlug(titleSlug);
        String slugFromUrl = extractSlugFromUrl(urlHint);
        String normalizedTitleHint = titleHint == null ? "" : titleHint.trim();
        String normalizedTitleKey = normalizeTitleKey(normalizedTitleHint);

        if (normalizedSlug.isBlank() && slugFromUrl.isBlank() && normalizedTitleHint.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Slug, URL, or title is required"));
        }

        try {
            String cacheTitleKeyTmp = normalizeTitleKey(normalizedTitleHint);
            Map<String, Object> cachedPayload = getCachedQuestionPayload(normalizedSlug, slugFromUrl, cacheTitleKeyTmp);
            if (cachedPayload != null) {
                return ResponseEntity.ok(cachedPayload);
            }

            List<String> slugCandidates = new ArrayList<>();
            if (!normalizedSlug.isBlank()) {
                slugCandidates.add(normalizedSlug);
            }
            if (!slugFromUrl.isBlank() && !slugCandidates.contains(slugFromUrl)) {
                slugCandidates.add(slugFromUrl);
            }

            for (String slugCandidate : slugCandidates) {
                Map<String, Object> payload = fetchQuestionBySlug(slugCandidate);
                if (hasQuestion(payload)) {
                    cacheQuestionPayload(payload, slugCandidate, normalizedTitleKey);
                    return ResponseEntity.ok(payload);
                }
            }

            if (!normalizedTitleHint.isBlank()) {
                String resolvedSlug = resolveSlugByTitle(normalizedTitleHint);
                if (!resolvedSlug.isBlank()) {
                    Map<String, Object> resolvedPayload = fetchQuestionBySlug(resolvedSlug);
                    if (hasQuestion(resolvedPayload)) {
                        cacheQuestionPayload(resolvedPayload, resolvedSlug, normalizedTitleKey);
                        return ResponseEntity.ok(resolvedPayload);
                    }
                }
            }

            return ResponseEntity.status(404).body(Map.of(
                    "error", "Question description not available on LeetCode for this entry.",
                    "slug", normalizedSlug,
                    "slugFromUrl", slugFromUrl,
                    "titleHint", normalizedTitleHint));
        } catch (Exception e) {
            logger.error("Error fetching question details for slug={} urlHint={} titleHint={}: {}",
                    normalizedSlug, urlHint, normalizedTitleHint, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch LeetCode question details"));
        }
    }

    public Map<String, Object> submitCodeToLeetCode(
            String problemSlug,
            String language,
            String sourceCode,
            String leetcodeSession,
            String csrfToken,
            boolean waitForResult) {

        String normalizedSlug = normalizeSlug(problemSlug);
        if (normalizedSlug.isBlank()) {
            throw new IllegalArgumentException("Problem slug is required.");
        }

        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("Source code cannot be empty.");
        }

        if (leetcodeSession == null || leetcodeSession.isBlank()) {
            throw new IllegalArgumentException("LEETCODE_SESSION is required.");
        }

        if (csrfToken == null || csrfToken.isBlank()) {
            throw new IllegalArgumentException("csrftoken is required.");
        }

        String leetCodeLanguage = resolveLeetCodeLanguageSlug(language);
        String questionId = fetchQuestionId(normalizedSlug);
        if (questionId.isBlank()) {
            throw new IllegalArgumentException("Unable to resolve LeetCode question ID for slug: " + normalizedSlug);
        }

        HttpHeaders headers = createSubmissionHeaders(csrfToken.trim(), leetcodeSession.trim(), normalizedSlug);
        Map<String, Object> payload = new HashMap<>();
        payload.put("lang", leetCodeLanguage);
        payload.put("question_id", questionId);
        payload.put("typed_code", sourceCode);

        try {
            String submitUrl = SUBMIT_URL_TEMPLATE.formatted(normalizedSlug);
            ResponseEntity<Map> submitResponse = restTemplate.exchange(
                    submitUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class);

            Map<String, Object> submitBody = submitResponse.getBody();
            String submissionId = asString(submitBody == null ? null : submitBody.get("submission_id"));
            if (submissionId == null || submissionId.isBlank()) {
                submissionId = asString(submitBody == null ? null : submitBody.get("submission_id_v2"));
            }

            if (submissionId == null || submissionId.isBlank()) {
                throw new IllegalArgumentException("LeetCode did not return a submission ID.");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("problemSlug", normalizedSlug);
            result.put("language", leetCodeLanguage);
            result.put("submissionId", submissionId);
            result.put("status", "SUBMITTED");
            result.put("leetcodeSubmissionUrl", "https://leetcode.com/submissions/detail/" + submissionId + "/");

            if (waitForResult) {
                Map<String, Object> judgeResult = pollSubmissionResult(submissionId, headers);
                String statusMessage = asString(judgeResult.get("status_msg"));
                if (statusMessage != null && !statusMessage.isBlank()) {
                    result.put("status", statusMessage);
                }
                result.put("judgeResult", judgeResult);
            }

            return result;
        } catch (HttpStatusCodeException ex) {
            String responseBody = shorten(ex.getResponseBodyAsString());
            logger.warn("LeetCode submission rejected for slug {}: status={}, body={}",
                    normalizedSlug, ex.getStatusCode(), responseBody);
            throw new IllegalArgumentException("LeetCode rejected submission: " + responseBody);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("LeetCode submission error for slug {}", normalizedSlug, ex);
            throw new RuntimeException("Could not submit to LeetCode right now.");
        }
    }

    private String fetchQuestionId(String titleSlug) {
        try {
            String query = """
                    query questionEditorData($titleSlug: String!) {
                      question(titleSlug: $titleSlug) {
                        questionId
                      }
                    }
                    """;

            Map<String, Object> payload = Map.of(
                    "query", query,
                    "variables", Map.of("titleSlug", titleSlug));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, createHeaders());
            Map<String, Object> response = restTemplate.postForObject(BASE_URL, entity, Map.class);
            Map<String, Object> data = asMap(response == null ? null : response.get("data"));
            Map<String, Object> question = asMap(data == null ? null : data.get("question"));
            return asString(question == null ? null : question.get("questionId"));
        } catch (Exception ex) {
            logger.warn("Failed to fetch question ID for slug {}: {}", titleSlug, ex.getMessage());
            return "";
        }
    }

    private Map<String, Object> fetchQuestionBySlug(String titleSlug) {
        String query = """
                query questionData($titleSlug: String!) {
                  question(titleSlug: $titleSlug) {
                    questionId
                    title
                    titleSlug
                    content
                    difficulty
                    topicTags {
                      name
                      slug
                    }
                    stats
                  }
                }
                """;

        Map<String, Object> variables = Map.of("titleSlug", titleSlug);
        Map<String, Object> body = Map.of("query", query, "variables", variables);
        return postGraphQlWithRetry(body);
    }

    private boolean hasQuestion(Map<String, Object> payload) {
        Map<String, Object> data = asMap(payload.get("data"));
        Map<String, Object> question = asMap(data == null ? null : data.get("question"));
        return question != null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCachedQuestionPayload(String slug, String slugFromUrl, String titleKey) {
        Cache cache = cacheManager.getCache("leetcodeQuestionDetails");
        if (cache == null)
            return null;

        for (String key : buildQuestionCacheKeys(slug, slugFromUrl, titleKey)) {
            Map<String, Object> entry = cache.get(key, Map.class);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private void cacheQuestionPayload(Map<String, Object> payload, String slugHint, String titleKeyHint) {
        if (!hasQuestion(payload)) {
            return;
        }

        Cache cache = cacheManager.getCache("leetcodeQuestionDetails");
        if (cache == null)
            return;

        Map<String, Object> data = asMap(payload.get("data"));
        Map<String, Object> question = asMap(data == null ? null : data.get("question"));
        String resolvedSlug = normalizeSlug(asString(question == null ? null : question.get("titleSlug")));

        String cacheSlug = !resolvedSlug.isBlank() ? resolvedSlug : normalizeSlug(slugHint);
        String cacheTitleKey = titleKeyHint == null ? "" : titleKeyHint;

        for (String key : buildQuestionCacheKeys(cacheSlug, "", cacheTitleKey)) {
            cache.put(key, payload);
        }
    }

    private List<String> buildQuestionCacheKeys(String slug, String slugFromUrl, String titleKey) {
        List<String> keys = new ArrayList<>();

        String normalizedSlug = normalizeSlug(slug);
        if (!normalizedSlug.isBlank()) {
            keys.add("slug:" + normalizedSlug);
        }

        String normalizedSlugFromUrl = normalizeSlug(slugFromUrl);
        if (!normalizedSlugFromUrl.isBlank() && !normalizedSlugFromUrl.equals(normalizedSlug)) {
            keys.add("slug:" + normalizedSlugFromUrl);
        }

        if (titleKey != null && !titleKey.isBlank()) {
            keys.add("title:" + titleKey);
        }

        return keys;
    }

    private String resolveSlugByTitle(String titleHint) {
        for (String variant : buildTitleSearchVariants(titleHint)) {
            try {
                String resolved = resolveSlugByTitleVariant(variant, titleHint);
                if (!resolved.isBlank()) {
                    return resolved;
                }
            } catch (Exception ex) {
                logger.debug("Failed resolving slug by title variant '{}': {}", variant, ex.getMessage());
            }
        }
        return "";
    }

    private String resolveSlugByTitleVariant(String searchKeyword, String originalTitleHint) {
        String query = """
                query problemsetQuestionList($categorySlug: String, $skip: Int, $limit: Int, $filters: QuestionListFilterInput) {
                  problemsetQuestionList(categorySlug: $categorySlug, skip: $skip, limit: $limit, filters: $filters) {
                    questions {
                      title
                      titleSlug
                    }
                  }
                }
                """;

        Map<String, Object> filters = Map.of("searchKeywords", searchKeyword);
        Map<String, Object> variables = new HashMap<>();
        variables.put("categorySlug", "");
        variables.put("skip", 0);
        variables.put("limit", PROBLEMSET_QUERY_LIMIT);
        variables.put("filters", filters);

        Map<String, Object> body = Map.of("query", query, "variables", variables);
        Map<String, Object> responseBody = postGraphQlWithRetry(body);
        Map<String, Object> data = asMap(responseBody == null ? null : responseBody.get("data"));
        Map<String, Object> list = asMap(data == null ? null : data.get("problemsetQuestionList"));
        List<Map<String, Object>> questions = asMapList(list == null ? null : list.get("questions"));
        if (questions.isEmpty()) {
            return "";
        }

        String targetTitleKey = normalizeTitleKey(originalTitleHint);
        String targetVariantKey = normalizeTitleKey(searchKeyword);

        for (Map<String, Object> question : questions) {
            String title = asString(question.get("title"));
            String slug = asString(question.get("titleSlug"));
            if (title == null || slug == null) {
                continue;
            }

            String currentTitleKey = normalizeTitleKey(title);
            if (currentTitleKey.equals(targetTitleKey) || currentTitleKey.equals(targetVariantKey)) {
                return normalizeSlug(slug);
            }
        }

        String firstSlug = asString(questions.get(0).get("titleSlug"));
        return normalizeSlug(firstSlug);
    }

    private List<String> buildTitleSearchVariants(String titleHint) {
        String raw = titleHint == null ? "" : titleHint.trim();
        if (raw.isBlank()) {
            return List.of();
        }

        List<String> variants = new ArrayList<>();
        variants.add(raw);

        String withoutSiteSuffix = raw
                .replaceAll("(?i)\\s*[|\\-:]\\s*leetcode\\s*$", "")
                .trim();
        if (!withoutSiteSuffix.isBlank() && !variants.contains(withoutSiteSuffix)) {
            variants.add(withoutSiteSuffix);
        }

        String withoutLeadingNumber = withoutSiteSuffix
                .replaceAll("^\\s*\\d+[\\.)\\-:\\s]+", "")
                .trim();
        if (!withoutLeadingNumber.isBlank() && !variants.contains(withoutLeadingNumber)) {
            variants.add(withoutLeadingNumber);
        }

        String asciiSimplified = withoutLeadingNumber
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (!asciiSimplified.isBlank() && !variants.contains(asciiSimplified)) {
            variants.add(asciiSimplified);
        }

        return variants;
    }

    private String normalizeTitleKey(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    private String extractSlugFromUrl(String urlHint) {
        if (urlHint == null || urlHint.isBlank()) {
            return "";
        }
        return normalizeSlug(urlHint);
    }

    private Map<String, Object> postGraphQlWithRetry(Map<String, Object> body) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= GRAPHQL_RETRY_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        BASE_URL,
                        new HttpEntity<>(body, createHeaders()),
                        Map.class);
                return response.getBody() == null ? Map.of() : response.getBody();
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < GRAPHQL_RETRY_ATTEMPTS) {
                    sleepForRetry();
                }
            }
        }

        if (lastException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException("LeetCode GraphQL request failed after retries.", lastException);
    }

    private void sleepForRetry() {
        try {
            Thread.sleep(GRAPHQL_RETRY_DELAY_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> pollSubmissionResult(String submissionId, HttpHeaders headers) {
        String checkUrl = SUBMISSION_CHECK_URL_TEMPLATE.formatted(submissionId);
        Map<String, Object> latestResponse = new HashMap<>();

        for (int attempt = 0; attempt < MAX_SUBMISSION_POLL_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<Map> checkResponse = restTemplate.exchange(
                        checkUrl,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class);

                Map<String, Object> body = checkResponse.getBody();
                if (body != null) {
                    latestResponse = body;
                    String state = asString(body.get("state"));
                    if (!isPendingState(state)) {
                        return latestResponse;
                    }
                }
            } catch (Exception ex) {
                logger.debug("LeetCode polling attempt {} failed: {}", attempt + 1, ex.getMessage());
            }

            if (attempt + 1 < MAX_SUBMISSION_POLL_ATTEMPTS) {
                sleepBeforeNextPoll();
            }
        }

        if (!latestResponse.containsKey("state")) {
            latestResponse.put("state", "TIMEOUT");
            latestResponse.put("status_msg", "Judge result not ready");
        }
        return latestResponse;
    }

    private boolean isPendingState(String state) {
        if (state == null || state.isBlank()) {
            return true;
        }
        return "PENDING".equalsIgnoreCase(state) || "STARTED".equalsIgnoreCase(state);
    }

    private void sleepBeforeNextPoll() {
        try {
            Thread.sleep(SUBMISSION_POLL_INTERVAL_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpHeaders createSubmissionHeaders(String csrfToken, String leetcodeSession, String problemSlug) {
        HttpHeaders headers = createHeaders();
        headers.set("Origin", "https://leetcode.com");
        headers.set("Referer", "https://leetcode.com/problems/" + problemSlug + "/");
        headers.set("x-csrftoken", csrfToken);
        headers.set("x-requested-with", "XMLHttpRequest");
        headers.set("Cookie", "LEETCODE_SESSION=%s; csrftoken=%s;".formatted(leetcodeSession, csrfToken));
        return headers;
    }

    private String resolveLeetCodeLanguageSlug(String language) {
        String normalized = language == null ? "" : language.trim().toLowerCase();
        return switch (normalized) {
            case "java" -> "java";
            case "python", "python3", "py" -> "python3";
            case "cpp", "c++" -> "cpp";
            case "javascript", "js", "node" -> "javascript";
            case "typescript", "ts" -> "typescript";
            default -> throw new IllegalArgumentException("Unsupported LeetCode language: " + language);
        };
    }

    private String normalizeSlug(String slug) {
        if (slug == null) {
            return "";
        }

        String trimmed = slug.trim();
        if (trimmed.contains("leetcode.com/problems/")) {
            int start = trimmed.indexOf("leetcode.com/problems/") + "leetcode.com/problems/".length();
            String tail = trimmed.substring(start);
            int slash = tail.indexOf('/');
            return slash >= 0 ? tail.substring(0, slash) : tail;
        }
        return trimmed;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Accept", "application/json");
        headers.add("Origin", "https://leetcode.com");
        headers.add("Referer", "https://leetcode.com/problemset/");
        headers.add("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                        + " Chrome/124.0.0.0 Safari/537.36");
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
    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return (Map<String, Object>) map;
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

    private String shorten(String text) {
        if (text == null || text.isBlank()) {
            return "No error details returned.";
        }
        if (text.length() <= 280) {
            return text;
        }
        return text.substring(0, 280) + "...";
    }
}
