package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.skilltracker.student_skill_tracker.compiler.CompilationResult;
import com.skilltracker.student_skill_tracker.dto.CodeExecutionRequest;
import com.skilltracker.student_skill_tracker.dto.jdoodle.JDoodleRequest;
import com.skilltracker.student_skill_tracker.dto.jdoodle.JDoodleResponse;

@Service
public class JDoodleCompilerService {

    private static final Logger logger = LoggerFactory.getLogger(JDoodleCompilerService.class);
    private static final String AUTH_FAILURE_PREFIX = "JDoodle authorization failed";
    private static final String JAVA_LANGUAGE_KEY = "java";
    private static final Pattern JAVA_MAIN_METHOD_PATTERN = Pattern
            .compile("\\b(?:public\\s+)?static\\s+void\\s+main\\s*\\(");
    private static final Pattern JAVA_PUBLIC_CLASS_PATTERN = Pattern
            .compile("\\bpublic\\s+(?:abstract\\s+|final\\s+)?class\\s+[A-Za-z_$][A-Za-z\\d_$]*");
    private static final Pattern JAVA_NON_PUBLIC_CLASS_PATTERN = Pattern
            .compile("\\b(?:(abstract|final)\\s+)?class\\s+([A-Za-z_$][A-Za-z\\d_$]*)");

    private final RestClient restClient;
    private final String apiUrl;
    private final String clientId;
    private final String clientSecret;

    public JDoodleCompilerService(
            @Value("${jdoodle.api.url:https://api.jdoodle.com/v1/execute}") String apiUrl,
            @Value("${jdoodle.client.id:}") String clientId,
            @Value("${jdoodle.client.secret:}") String clientSecret) {
        this.apiUrl = apiUrl;
        this.clientId = resolveCredential(clientId, "JDOODLE_CLIENT_ID", "jdoodle.client.id");
        this.clientSecret = resolveCredential(clientSecret, "JDOODLE_CLIENT_SECRET", "jdoodle.client.secret");
        this.restClient = RestClient.create();
        logger.info("JDoodle credential presence at startup: clientIdPresent={}, clientSecretPresent={}",
                StringUtils.hasText(this.clientId), StringUtils.hasText(this.clientSecret));
    }

    public CompilationResult executeRemotely(CodeExecutionRequest request) {
        logger.info("Executing code remotely via JDoodle API for language: {}", request.getLanguage());
        if (!isConfigured()) {
            return CompilationResult.builder()
                    .success(false)
                    .error("JDoodle credentials are missing on server (JDOODLE_CLIENT_ID/JDOODLE_CLIENT_SECRET).")
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        String script = request.getSourceCode();
        if (isJava(request.getLanguage())) {
            JavaRemotePreparation preparation = prepareJavaForRemoteExecution(script);
            if (!preparation.shouldExecuteRemotely()) {
                return buildNoMainGuidanceResult(request.getLanguage());
            }
            script = preparation.script();
        }

        // 1. Map request to JDoodleRequest DTO
        JDoodleRequest jdoodleReq = JDoodleRequest.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .script(script)
                .language(mapLanguageToJDoodleKey(request.getLanguage()))
                .versionIndex(mapVersionIndex(request.getLanguage()))
                .stdin(request.getInput())
                .build();

        try {
            // 2. Call JDoodle API using RestClient
            JDoodleResponse response = restClient.post()
                    .uri(apiUrl)
                    .header("Content-Type", "application/json")
                    .body(jdoodleReq)
                    .retrieve()
                    .body(JDoodleResponse.class);

            if (response == null) {
                return CompilationResult.builder()
                        .success(false)
                        .error("Empty response from JDoodle API")
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 3. Map JDoodleResponse back to your CompilationResult
            boolean success = response.getStatusCode() == 200;
            if (isJava(request.getLanguage()) && looksLikeMissingJavaEntrypoint(response.getOutput())) {
                return buildNoMainGuidanceResult(request.getLanguage());
            }

            return CompilationResult.builder()
                    .success(success)
                    .output(response.getOutput()) // Output contains both stdout and stderr in JDoodle
                    .error(response.getError() != null ? response.getError() : "")
                    .executionTime(response.getCpuTime() != null ? response.getCpuTime() + "s" : "remote")
                    .language(request.getLanguage())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                logger.error("JDoodle returned unauthorized status {}. Verify credentials in environment variables.",
                        status);
                return CompilationResult.builder()
                        .success(false)
                        .error(AUTH_FAILURE_PREFIX + " (HTTP " + status
                                + "). Check JDOODLE_CLIENT_ID/JDOODLE_CLIENT_SECRET on Render.")
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            logger.error("JDoodle remote execution failed with HTTP status {}", status, e);
            return CompilationResult.builder()
                    .success(false)
                    .error("Remote execution failed: JDoodle HTTP " + status)
                    .timestamp(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            logger.error("JDoodle remote execution failed", e);
            return CompilationResult.builder()
                    .success(false)
                    .error("Remote execution failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    public boolean isAuthorizationFailure(CompilationResult result) {
        return result != null
                && !result.isSuccess()
                && result.getError() != null
                && result.getError().startsWith(AUTH_FAILURE_PREFIX);
    }

    private String resolveCredential(String configuredValue, String uppercaseEnvKey, String lowercaseEnvKey) {
        if (StringUtils.hasText(configuredValue)) {
            return configuredValue.trim();
        }

        String upperEnv = System.getenv(uppercaseEnvKey);
        if (StringUtils.hasText(upperEnv)) {
            return upperEnv.trim();
        }

        String lowerEnv = System.getenv(lowercaseEnvKey);
        if (StringUtils.hasText(lowerEnv)) {
            return lowerEnv.trim();
        }

        return "";
    }

    private boolean isJava(String language) {
        return JAVA_LANGUAGE_KEY.equalsIgnoreCase(language);
    }

    private JavaRemotePreparation prepareJavaForRemoteExecution(String sourceCode) {
        if (!JAVA_MAIN_METHOD_PATTERN.matcher(sourceCode).find()) {
            return new JavaRemotePreparation(sourceCode, false);
        }

        if (JAVA_PUBLIC_CLASS_PATTERN.matcher(sourceCode).find()) {
            return new JavaRemotePreparation(sourceCode, true);
        }

        Matcher nonPublicClassMatcher = JAVA_NON_PUBLIC_CLASS_PATTERN.matcher(sourceCode);
        if (!nonPublicClassMatcher.find()) {
            return new JavaRemotePreparation(sourceCode, true);
        }

        String classModifier = nonPublicClassMatcher.group(1);
        String replacement = StringUtils.hasText(classModifier)
                ? "public " + classModifier + "class Main"
                : "public class Main";
        String normalizedSource = nonPublicClassMatcher.replaceFirst(replacement);
        return new JavaRemotePreparation(normalizedSource, true);
    }

    private boolean looksLikeMissingJavaEntrypoint(String output) {
        return StringUtils.hasText(output)
                && output.toLowerCase().contains("no \"public class\" found to execute");
    }

    private CompilationResult buildNoMainGuidanceResult(String language) {
        return CompilationResult.builder()
                .success(true)
                .output("Compilation successful. No main() method detected, so there is nothing to run locally.\n"
                        + "This is expected for LeetCode-style class-only solutions. Use Submit To LeetCode to execute against test cases.")
                .error("")
                .language(language)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private record JavaRemotePreparation(String script, boolean shouldExecuteRemotely) {
    }

    private String mapLanguageToJDoodleKey(String language) {
        if (language == null)
            return "python3";
        return switch (language.toLowerCase()) {
            case "java" -> "java";
            case "python", "python3", "py" -> "python3";
            case "cpp", "c++" -> "cpp17";
            case "javascript", "js", "node" -> "nodejs";
            default -> "python3";
        };
    }

    // JDoodle requires specific version indexes (0, 1, 2, 3...) per language
    private String mapVersionIndex(String language) {
        if (language == null)
            return "4"; // default python3
        return switch (language.toLowerCase()) {
            case "java" -> "5"; // JDK 17.0.1
            case "python", "python3", "py" -> "4"; // Python 3.9.9
            case "cpp", "c++" -> "1"; // GCC 11.2.0
            case "javascript", "js", "node" -> "4"; // NodeJS 17.1.0
            default -> "4";
        };
    }
}
