package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.skilltracker.student_skill_tracker.compiler.CompilationResult;
import com.skilltracker.student_skill_tracker.dto.CodeExecutionRequest;
import com.skilltracker.student_skill_tracker.dto.jdoodle.JDoodleRequest;
import com.skilltracker.student_skill_tracker.dto.jdoodle.JDoodleResponse;

@Service
public class JDoodleCompilerService {

    private static final Logger logger = LoggerFactory.getLogger(JDoodleCompilerService.class);

    private final RestClient restClient;
    private final String apiUrl;
    private final String clientId;
    private final String clientSecret;

    public JDoodleCompilerService(
            @Value("${jdoodle.api.url:https://api.jdoodle.com/v1/execute}") String apiUrl,
            @Value("${jdoodle.client.id:}") String clientId,
            @Value("${jdoodle.client.secret:}") String clientSecret) {
        this.apiUrl = apiUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.create();
    }

    public CompilationResult executeRemotely(CodeExecutionRequest request) {
        logger.info("Executing code remotely via JDoodle API for language: {}", request.getLanguage());

        // 1. Map request to JDoodleRequest DTO
        JDoodleRequest jdoodleReq = JDoodleRequest.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .script(request.getSourceCode())
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

            return CompilationResult.builder()
                    .success(success)
                    .output(response.getOutput()) // Output contains both stdout and stderr in JDoodle
                    .error(response.getError() != null ? response.getError() : "")
                    .executionTime(response.getCpuTime() != null ? response.getCpuTime() + "s" : "remote")
                    .language(request.getLanguage())
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
