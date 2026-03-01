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

import com.skilltracker.student_skill_tracker.util.SecurityUtils;

public class PythonCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "Python";
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String PYTHON_CMD;

    static {
        // Windows uses 'python', Linux/Mac uses 'python3'
        String cmd = "python3";
        try {
            Process p = new ProcessBuilder("python", "--version").redirectErrorStream(true).start();
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                cmd = "python";
            }
        } catch (Exception e) {
            // python not found, try python3
        }
        PYTHON_CMD = cmd;
    }

    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        CompilationResult result = new CompilationResult();

        // RCE Security Layer: Block malicious keywords
        if (SecurityUtils.containsMaliciousKeywords(sourceCode)) {
            result.setSuccess(false);
            result.setError("Security Violation: Malicious keywords detected in code.");
            return result;
        }

        String uniqueId = UUID.randomUUID().toString();
        String fileName = "solution_" + uniqueId + ".py";
        String filePath = TEMP_DIR + File.separator + fileName;

        try {
            // Write Python code
            Files.write(Paths.get(filePath), sourceCode.getBytes());

            // Execute
            ProcessBuilder runBuilder = new ProcessBuilder(PYTHON_CMD, filePath);
            runBuilder.redirectErrorStream(true);
            Process runProcess = runBuilder.start();

            // Send input
            if (input != null && !input.isEmpty()) {
                try (OutputStream os = runProcess.getOutputStream()) {
                    os.write(input.getBytes());
                    os.flush();
                }
            }

            // Wait for completion (DoS Defense)
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
                if (output != null && output.contains("NameError")) {
                    runtimeDetails = "Runtime error: Python NameError.\n"
                            + "If this is a LeetCode-style solution, ensure your local script invokes the function/class with test input.\n\n"
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
            ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, "--version");
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
            ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, "--version");
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
