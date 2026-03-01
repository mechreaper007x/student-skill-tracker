package com.skilltracker.student_skill_tracker.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class CppCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "C++";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final Pattern MAIN_FUNCTION_PATTERN = Pattern.compile("\\bmain\\s*\\(");

    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        CompilationResult result = new CompilationResult();
        Path workspace = null;
        boolean hasMainFunction = MAIN_FUNCTION_PATTERN.matcher(sourceCode).find();

        try {
            ExecutionSandbox.validateSourceSize(sourceCode);
            workspace = ExecutionSandbox.createWorkspace("cpp");
            Path sourceFilePath = workspace.resolve("solution.cpp");
            Path exePath = workspace.resolve(IS_WINDOWS ? "solution.exe" : "solution");

            // Write C++ code
            Files.writeString(sourceFilePath, sourceCode, StandardCharsets.UTF_8);

            // LeetCode-style C++ solutions often do not define main().
            // For local run, syntax-check only and return guidance.
            if (!hasMainFunction) {
                ExecutionSandbox.ProcessResult syntaxResult = ExecutionSandbox.run(
                        workspace,
                        List.of("g++", "-fsyntax-only", sourceFilePath.toString(), "-std=c++17"),
                        "",
                        15);

                if (syntaxResult.timedOut()) {
                    result.setSuccess(false);
                    result.setError("Compilation timeout");
                    return result;
                }

                int syntaxExitCode = syntaxResult.exitCode();
                if (syntaxExitCode != 0) {
                    String compileError = syntaxResult.output();
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
            ExecutionSandbox.ProcessResult compileResult = ExecutionSandbox.run(
                    workspace,
                    List.of("g++", "-o", exePath.toString(), sourceFilePath.toString(), "-std=c++17"),
                    "",
                    15);

            if (compileResult.timedOut()) {
                result.setSuccess(false);
                result.setError("Compilation timeout");
                return result;
            }

            int compileExitCode = compileResult.exitCode();
            if (compileExitCode != 0) {
                String compileError = compileResult.output();
                result.setSuccess(false);
                result.setError("Compilation error:\n" + compileError);
                return result;
            }

            // Execute (DoS Layer: Resource limits can be added here if OS supports)
            ExecutionSandbox.ProcessResult runResult = ExecutionSandbox.run(
                    workspace,
                    List.of(exePath.toString()),
                    input,
                    timeoutSeconds);

            if (runResult.timedOut()) {
                result.setSuccess(false);
                result.setError("Execution timeout (" + timeoutSeconds + " seconds)");
                return result;
            }

            // Capture output
            String output = runResult.output();
            int exitCode = runResult.exitCode();

            result.setSuccess(exitCode == 0);
            result.setOutput(output);
            if (exitCode != 0) {
                String runtimeDetails = (output == null || output.isBlank())
                        ? "Runtime error (exit code: " + exitCode + ")"
                        : "Runtime error (exit code: " + exitCode + "):\n" + output;
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
        return ExecutionSandbox.isCommandAvailable(List.of("g++", "--version"), 5);
    }

    @Override
    public String getLanguageVersion() {
        String output = ExecutionSandbox.readCommandOutput(List.of("g++", "--version"), 5);
        if (output == null || output.isBlank()) {
            return "Unknown";
        }
        String[] lines = output.split("\n");
        return lines.length == 0 ? "Unknown" : lines[0];
    }
}
