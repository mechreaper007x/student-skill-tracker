package com.skilltracker.student_skill_tracker.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaScriptCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "JavaScript";
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        CompilationResult result = new CompilationResult();
        String uniqueId = UUID.randomUUID().toString();
        String fileName = "solution_" + uniqueId + ".js";
        String filePath = TEMP_DIR + File.separator + fileName;

        try {
            // Write JavaScript code
            Files.write(Paths.get(filePath), sourceCode.getBytes());

            // Execute with Node.js
            ProcessBuilder runBuilder = new ProcessBuilder("node", filePath);
            runBuilder.redirectErrorStream(true);
            Process runProcess = runBuilder.start();

            // Send input
            if (input != null && !input.isEmpty()) {
                try (OutputStream os = runProcess.getOutputStream()) {
                    os.write(input.getBytes());
                    os.flush();
                }
            }

            // Wait for completion
            boolean completed = runProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                runProcess.destroyForcibly();
                result.setSuccess(false);
                result.setError("Execution timeout (" + timeoutSeconds + " seconds)");
                return result;
            }

            // Capture output
            String output = readStream(runProcess.getInputStream());
            int exitCode = runProcess.exitValue();

            result.setSuccess(exitCode == 0);
            result.setOutput(output);
            if (exitCode != 0) {
                String runtimeDetails;
                if (output != null && output.contains("ReferenceError")) {
                    runtimeDetails = "Runtime error: JavaScript ReferenceError.\n"
                            + "If this is a LeetCode-style solution, ensure your local script calls the function with test input.\n\n"
                            + output;
                } else if (output == null || output.isBlank()) {
                    runtimeDetails = "Runtime error (exit code: " + exitCode + ")";
                } else {
                    runtimeDetails = "Runtime error (exit code: " + exitCode + "):\n" + output;
                }
                result.setError(runtimeDetails);
            }

        } catch (IOException | InterruptedException e) {
            result.setSuccess(false);
            result.setError("Exception: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                // Ignore
            }
        }

        return result;
    }

    @Override
    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    @Override
    public boolean isLanguageAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getLanguageVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return readStream(p.getInputStream());
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString().trim();
    }
}
