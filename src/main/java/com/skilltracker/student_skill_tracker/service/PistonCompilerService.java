package com.skilltracker.student_skill_tracker.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.skilltracker.student_skill_tracker.compiler.CompilationResult;
import com.skilltracker.student_skill_tracker.dto.CodeExecutionRequest;
import com.skilltracker.student_skill_tracker.dto.piston.PistonRequest;
import com.skilltracker.student_skill_tracker.dto.piston.PistonResponse;

@Service
public class PistonCompilerService {

    private static final Logger logger = LoggerFactory.getLogger(PistonCompilerService.class);

    private final RestClient restClient;
    private final String apiUrl;

    public PistonCompilerService(@Value("${piston.api.url:https://emkc.org/api/v2/piston/execute}") String apiUrl) {
        this.apiUrl = apiUrl;
        this.restClient = RestClient.create();
    }

    public CompilationResult executeRemotely(CodeExecutionRequest request) {
        logger.info("Executing code remotely via Piston API for language: {}", request.getLanguage());

        // 1. Map request to PistonRequest DTO
        PistonRequest pistonReq = PistonRequest.builder()
                .language(request.getLanguage())
                .version("*") // Piston picks latest if "*" is sent
                .files(List.of(new PistonRequest.PistonFile("solution", request.getSourceCode())))
                .stdin(request.getInput())
                .build();

        try {
            // 2. Call Piston API using RestClient
            PistonResponse response = restClient.post()
                    .uri(apiUrl)
                    .body(pistonReq)
                    .retrieve()
                    .body(PistonResponse.class);

            if (response == null || response.getRun() == null) {
                return CompilationResult.builder()
                        .success(false)
                        .error("Empty response from Piston API")
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            // 3. Map PistonResponse back to your CompilationResult
            PistonResponse.PistonResult run = response.getRun();

            String output = run.getOutput() != null ? run.getOutput()
                    : (run.getStdout() != null ? run.getStdout() : "")
                            + (run.getStderr() != null ? run.getStderr() : "");

            return CompilationResult.builder()
                    .success(run.getCode() == 0) // Success if exit code is 0
                    .output(output) // Combined stdout/stderr
                    .error(run.getStderr()) // Error stream
                    .executionTime("remote")
                    .language(request.getLanguage())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            logger.error("Piston remote execution failed", e);
            return CompilationResult.builder()
                    .success(false)
                    .error("Remote execution failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
}
