package com.skilltracker.student_skill_tracker.compiler;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import com.skilltracker.student_skill_tracker.dto.piston.PistonRequest;
import com.skilltracker.student_skill_tracker.dto.piston.PistonResponse;

public class PistonCompilerProvider implements ProgrammingLanguageCompiler {

    private static final Logger logger = LoggerFactory.getLogger(PistonCompilerProvider.class);
    private final String language;
    private final String version;
    private final String apiUrl;
    private final RestTemplate restTemplate;

    // Default versions as of 2025
    private static final Map<String, String> VERSION_MAP = Map.of(
            "java", "15.10.0",
            "python", "3.10.0",
            "cpp", "10.2.0",
            "javascript", "18.15.0");

    private static final java.util.regex.Pattern PUBLIC_CLASS_PATTERN = java.util.regex.Pattern
            .compile("\\bpublic\\s+class\\s+([A-Za-z_$][A-Za-z\\d_$]*)");

    public PistonCompilerProvider(String language, String apiUrl) { // Modified constructor as per instruction
        this.language = language;
        this.apiUrl = apiUrl; // Initialized as per instruction
        this.version = VERSION_MAP.getOrDefault(language, "latest");
        this.restTemplate = new RestTemplate(); // Initialized here as it's no longer static
    }

    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        logger.info("Offloading {} execution to Piston API", language);

        String filename = "script." + language;
        if ("java".equals(language)) {
            java.util.regex.Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
            if (matcher.find()) {
                filename = matcher.group(1) + ".java";
            } else {
                filename = "Solution.java";
            }
        }

        PistonRequest request = PistonRequest.builder()
                .language(language)
                .version(version)
                .stdin(input)
                .files(Collections.singletonList(new PistonRequest.PistonFile(filename, sourceCode)))
                .build();

        try {
            PistonResponse response = restTemplate.postForObject(apiUrl, request, PistonResponse.class);

            if (response == null || response.getRun() == null) {
                return CompilationResult.builder()
                        .success(false)
                        .error("Empty response from Piston API")
                        .timestamp(LocalDateTime.now())
                        .build();
            }

            PistonResponse.PistonResult run = response.getRun();
            boolean success = run.getCode() == 0 && (run.getStderr() == null || run.getStderr().isBlank());

            return CompilationResult.builder()
                    .success(success)
                    .output(run.getStdout())
                    .error(run.getStderr())
                    .executionTime("remote")
                    .language(language)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            logger.error("Piston execution failed", e);
            return CompilationResult.builder()
                    .success(false)
                    .error("Remote execution failed: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public String getLanguageName() {
        return language;
    }

    @Override
    public boolean isLanguageAvailable() {
        return true; // Always available via API
    }

    @Override
    public String getLanguageVersion() {
        return version;
    }
}
