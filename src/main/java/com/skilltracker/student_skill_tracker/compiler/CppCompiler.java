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
import java.util.regex.Pattern;

import com.skilltracker.student_skill_tracker.util.SecurityUtils;

public class CppCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "C++";
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final Pattern MAIN_FUNCTION_PATTERN = Pattern.compile("\\bmain\\s*\\(");

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
        String sourceFile = "solution_" + uniqueId + ".cpp";
        String exePath = TEMP_DIR + File.separator + "solution_" + uniqueId + (IS_WINDOWS ? ".exe" : "");
        String sourceFilePath = TEMP_DIR + File.separator + sourceFile;
        boolean hasMainFunction = MAIN_FUNCTION_PATTERN.matcher(sourceCode).find();

        try {
            // Write C++ code
            Files.write(Paths.get(sourceFilePath), sourceCode.getBytes());

            // LeetCode-style C++ solutions often do not define main().
            // For local run, syntax-check only and return guidance.
            if (!hasMainFunction) {
                ProcessBuilder syntaxCheckBuilder = new ProcessBuilder(
                        "g++", "-fsyntax-only", sourceFilePath, "-std=c++17");
                syntaxCheckBuilder.redirectErrorStream(true);
                Process syntaxCheckProcess = syntaxCheckBuilder.start();

                if (!syntaxCheckProcess.waitFor(15, TimeUnit.SECONDS)) {
                    syntaxCheckProcess.destroyForcibly();
                    result.setSuccess(false);
                    result.setError("Compilation timeout");
                    return result;
                }

                int syntaxExitCode = syntaxCheckProcess.exitValue();
                if (syntaxExitCode != 0) {
                    String compileError = readStream(syntaxCheckProcess.getInputStream());
                    result.setSuccess(false);
                    result.setError("Compilation error:\n" + compileError);
                    return result;
                }

                result.setSuccess(true);
                result.setError("");
                result.setOutput("Compilation successful. No main() function detected, so there is nothing to run locally.\n"
                        + "This is expected for LeetCode-style class/function-only solutions. "
                        + "Use Submit To LeetCode to execute against test cases.");
                return result;
            }

            // Compile and link executable when main() exists.
            ProcessBuilder compileBuilder = new ProcessBuilder(
                    "g++", "-o", exePath, sourceFilePath, "-std=c++17");
            compileBuilder.redirectErrorStream(true);
            Process compileProcess = compileBuilder.start();

            if (!compileProcess.waitFor(15, TimeUnit.SECONDS)) {
                compileProcess.destroyForcibly();
                result.setSuccess(false);
                result.setError("Compilation timeout");
                return result;
            }

            int compileExitCode = compileProcess.exitValue();
            if (compileExitCode != 0) {
                String compileError = readStream(compileProcess.getInputStream());
                result.setSuccess(false);
                result.setError("Compilation error:\n" + compileError);
                return result;
            }

            // Execute (DoS Layer: Resource limits can be added here if OS supports)
            ProcessBuilder runBuilder = new ProcessBuilder(exePath);
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
                String runtimeDetails = (output == null || output.isBlank())
                        ? "Runtime error (exit code: " + exitCode + ")"
                        : "Runtime error (exit code: " + exitCode + "):\n" + output;
                result.setError(runtimeDetails);
            }

        } catch (IOException | InterruptedException e) {
            result.setSuccess(false);
            result.setError("Exception: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(Paths.get(sourceFilePath));
                Files.deleteIfExists(Paths.get(exePath));
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
            ProcessBuilder pb = new ProcessBuilder("g++", "--version");
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
            ProcessBuilder pb = new ProcessBuilder("g++", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return readStream(p.getInputStream()).split("\n")[0];
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
