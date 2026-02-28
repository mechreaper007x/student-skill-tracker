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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.skilltracker.student_skill_tracker.util.SecurityUtils;

public class JavaCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "Java";
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern
            .compile("\\bpublic\\s+class\\s+([A-Za-z_$][A-Za-z\\d_$]*)");
    private static final Pattern CLASS_PATTERN = Pattern
            .compile("\\bclass\\s+([A-Za-z_$][A-Za-z\\d_$]*)");
    private static final Pattern MAIN_METHOD_PATTERN = Pattern
            .compile("\\b(?:public\\s+)?static\\s+void\\s+main\\s*\\(");

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
        String generatedClassName = "Solution_" + uniqueId.replace("-", "");
        String filePath = TEMP_DIR + File.separator + generatedClassName + ".java";
        String runClassName = generatedClassName;

        try {
            // Step 1: Normalize class name so launch target matches compiled class.
            String modifiedSource = sourceCode;
            Matcher publicClassMatcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
            if (publicClassMatcher.find()) {
                modifiedSource = publicClassMatcher.replaceFirst("public class " + generatedClassName);
                runClassName = generatedClassName;
            } else {
                Matcher classMatcher = CLASS_PATTERN.matcher(sourceCode);
                if (classMatcher.find()) {
                    runClassName = classMatcher.group(1);
                }
            }

            // Step 2: Write source code to file
            Files.write(Paths.get(filePath), modifiedSource.getBytes());

            // Step 3: Compile
            ProcessBuilder compileBuilder = new ProcessBuilder("javac", filePath);
            compileBuilder.redirectErrorStream(true);
            Process compileProcess = compileBuilder.start();

            if (!compileProcess.waitFor(10, TimeUnit.SECONDS)) {
                compileProcess.destroyForcibly();
                result.setSuccess(false);
                result.setError("Compilation timeout");
                return result;
            }

            int compileExitCode = compileProcess.exitValue();
            if (compileExitCode != 0) {
                String compileError = readStream(compileProcess.getInputStream());
                result.setSuccess(false);
                result.setError("Compilation error: " + compileError);
                return result;
            }

            // LeetCode-style Java solutions often have no main(). In that case, local run
            // should report compile success with guidance instead of a runtime failure.
            if (!MAIN_METHOD_PATTERN.matcher(modifiedSource).find()) {
                result.setSuccess(true);
                result.setError("");
                result.setOutput("Compilation successful. No main() method detected, so there is nothing to run locally.\n"
                        + "This is expected for LeetCode-style class-only solutions. Use Submit To LeetCode to execute against test cases.");
                return result;
            }

            // Step 4: Execute
            File tempFile = new File(filePath);
            String classPath = tempFile.getParent();

            // DoS Security Layer: Add Xshare:off and limit execution resources
            ProcessBuilder runBuilder = new ProcessBuilder("java", "-Xshare:off", "-Xmx128m", "-cp", classPath, runClassName);
            runBuilder.redirectErrorStream(true);
            Process runProcess = runBuilder.start();

            // Step 5: Send input
            if (input != null && !input.isEmpty()) {
                try (OutputStream os = runProcess.getOutputStream()) {
                    os.write(input.getBytes());
                    os.flush();
                }
            }

            // Step 6: Wait with timeout (DoS Defense)
            boolean completed = runProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                runProcess.destroyForcibly();
                result.setSuccess(false);
                result.setError("Execution timeout (" + timeoutSeconds + " seconds)");
                return result;
            }

            // Step 7: Capture output
            String output = readStream(runProcess.getInputStream());
            int exitCode = runProcess.exitValue();

            result.setSuccess(exitCode == 0);
            if (exitCode == 0) {
                result.setOutput(output);
            } else {
                result.setOutput("");
                String runtimeDetails;
                if (output != null && output.contains("Main method not found")) {
                    runtimeDetails = "Runtime error: no Java main method found.\n"
                            + "Local Run executes programs with an entry point. "
                            + "LeetCode-style class-only solutions need a temporary main() driver for local execution.\n\n"
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
                Files.deleteIfExists(Paths.get(TEMP_DIR + File.separator + generatedClassName + ".class"));
                if (!generatedClassName.equals(runClassName)) {
                    Files.deleteIfExists(Paths.get(TEMP_DIR + File.separator + runClassName + ".class"));
                }
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
            ProcessBuilder pb = new ProcessBuilder("java", "-Xshare:off", "-version");
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
            ProcessBuilder pb = new ProcessBuilder("java", "-Xshare:off", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = readStream(p.getInputStream());
            return output.split("\n")[0];
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
