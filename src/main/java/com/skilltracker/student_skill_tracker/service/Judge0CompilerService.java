package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.skilltracker.student_skill_tracker.compiler.CompilationResult;
import com.skilltracker.student_skill_tracker.dto.CodeExecutionRequest;
import com.skilltracker.student_skill_tracker.dto.judge0.Judge0Request;
import com.skilltracker.student_skill_tracker.dto.judge0.Judge0Response;

@Service
public class Judge0CompilerService {

    private static final Logger logger = LoggerFactory.getLogger(Judge0CompilerService.class);

    private final RestClient restClient;
    private final String apiUrl;
    private final String rapidApiKey;
    private final String rapidApiHost;

    public Judge0CompilerService(
            @Value("${judge0.api.url:https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=false&wait=true}") String apiUrl,
            @Value("${judge0.api.key:}") String rapidApiKey,
            @Value("${judge0.api.host:judge0-ce.p.rapidapi.com}") String rapidApiHost) {
        this.apiUrl = apiUrl;
        this.rapidApiKey = rapidApiKey;
        this.rapidApiHost = rapidApiHost;
        this.restClient = RestClient.create();
    }

    public CompilationResult executeRemotely(CodeExecutionRequest request) {
        logger.info("Executing code remotely via Judge0 API for language: {}", request.getLanguage());

        // 1. Map request to Judge0Request DTO
        Judge0Request judge0Req = Judge0Request.builder()
                .language_id(mapLanguageToJudge0Id(request.getLanguage()))
                .source_code(request.getSourceCode())
                .stdin(request.getInput())
                .build();

        try {
            // 2. Call Judge0 API using RestClient
            Judge0Response response = restClient.post()
                    .uri(apiUrl)
                    .header("x-rapidapi-key", rapidApiKey)
                    .header("x-rapidapi-host", rapidApiHost)
                    .header("Content-Type", "application/json")
                    .body(judge0Req)
                    .retrieve()
                    .body(Judge0Response.class);

            if (response == null || response.getStatus() == null) {
                return CompilationResult.builder()
                        .success(false)
                        .error("Empty response from Judge0 API")
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 3. Map Judge0Response back to your CompilationResult
            boolean success = response.getStatus().getId() == 3; // 3 = Accepted

            String output = response.getStdout() != null ? response.getStdout() : "";
            String errorOutput = response.getStderr() != null ? response.getStderr() : "";
            if (response.getCompile_output() != null && !response.getCompile_output().isBlank()) {
                errorOutput = response.getCompile_output() + "\n" + errorOutput;
            }
            if (!success && errorOutput.isBlank() && response.getMessage() != null) {
                errorOutput = response.getMessage() + "\nStatus: " + response.getStatus().getDescription();
            }

            return CompilationResult.builder()
                    .success(success)
                    .output(output + errorOutput) // UI combines these often, or keep separate
                    .error(errorOutput)
                    .executionTime(response.getTime() != null ? response.getTime() + "s" : "remote")
                    .language(request.getLanguage())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            logger.error("Judge0 remote execution failed", e);
            return CompilationResult.builder()
                    .success(false)
                    .error("Remote execution failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    private int mapLanguageToJudge0Id(String language) {
        if (language == null)
            return 71; // Default to Python or standard error
        return switch (language.toLowerCase()) {
            case "java" -> 62; // Java (OpenJDK 13.0.1)
            case "python", "python3", "py" -> 71; // Python (3.8.1)
            case "cpp", "c++" -> 54; // C++ (GCC 9.2.0)
            case "javascript", "js", "node" -> 63; // Node.js (12.14.0)
            default -> 71;
        };
    }
}
