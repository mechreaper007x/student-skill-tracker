package com.skilltracker.student_skill_tracker.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "Java";
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern
            .compile("\\bpublic\\s+class\\s+([A-Za-z_$][A-Za-z\\d_$]*)");
    private static final Pattern CLASS_PATTERN = Pattern
            .compile("\\bclass\\s+([A-Za-z_$][A-Za-z\\d_$]*)");
    private static final Pattern MAIN_METHOD_PATTERN = Pattern
            .compile("\\b(?:public\\s+)?static\\s+void\\s+main\\s*\\(");

    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        CompilationResult result = new CompilationResult();
        Path workspace = null;
        String generatedClassName = "Solution_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String runClassName = generatedClassName;

        try {
            ExecutionSandbox.validateSourceSize(sourceCode);
            workspace = ExecutionSandbox.createWorkspace("java");
            Path sourceFile = workspace.resolve(generatedClassName + ".java");

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
            Files.writeString(sourceFile, modifiedSource, StandardCharsets.UTF_8);

            // Step 3: Compile
            ExecutionSandbox.ProcessResult compileResult = ExecutionSandbox.run(
                    workspace,
                    List.of("javac", sourceFile.toString()),
                    "",
                    10);

            if (compileResult.timedOut()) {
                result.setSuccess(false);
                result.setError("Compilation timeout");
                return result;
            }

            int compileExitCode = compileResult.exitCode();
            if (compileExitCode != 0) {
                String compileError = compileResult.output();
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
            String classPath = workspace.toString();

            // DoS Security Layer: Add Xshare:off and limit execution resources
            ExecutionSandbox.ProcessResult runResult = ExecutionSandbox.run(
                    workspace,
                    List.of("java", "-Xshare:off", "-Xmx128m", "-cp", classPath, runClassName),
                    input,
                    timeoutSeconds);

            if (runResult.timedOut()) {
                result.setSuccess(false);
                result.setError("Execution timeout (" + timeoutSeconds + " seconds)");
                return result;
            }

            // Step 7: Capture output
            String output = runResult.output();
            int exitCode = runResult.exitCode();

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

        } catch (IllegalArgumentException e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError("Exception: " + e.getMessage());
        } finally {
            ExecutionSandbox.deleteWorkspace(workspace);
        }

        return result;
    }

    @Override
    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    @Override
    public boolean isLanguageAvailable() {
        return ExecutionSandbox.isCommandAvailable(List.of("java", "-Xshare:off", "-version"), 5);
    }

    @Override
    public String getLanguageVersion() {
        String output = ExecutionSandbox.readCommandOutput(List.of("java", "-Xshare:off", "-version"), 5);
        if (output == null || output.isBlank()) {
            return "Unknown";
        }
        String[] lines = output.split("\n");
        return lines.length == 0 ? "Unknown" : lines[0];
    }
}
