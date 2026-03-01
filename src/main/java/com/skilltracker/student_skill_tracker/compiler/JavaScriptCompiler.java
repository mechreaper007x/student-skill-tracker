package com.skilltracker.student_skill_tracker.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class JavaScriptCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "JavaScript";

    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        CompilationResult result = new CompilationResult();
        Path workspace = null;

        try {
            ExecutionSandbox.validateSourceSize(sourceCode);
            workspace = ExecutionSandbox.createWorkspace("javascript");
            Path sourceFile = workspace.resolve("solution.js");
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

            ExecutionSandbox.ProcessResult runResult = ExecutionSandbox.run(
                    workspace,
                    List.of("node", "--max-old-space-size=96", "--disallow-code-generation-from-strings",
                            sourceFile.toString()),
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
        return ExecutionSandbox.isCommandAvailable(List.of("node", "--version"), 5);
    }

    @Override
    public String getLanguageVersion() {
        return ExecutionSandbox.readCommandOutput(List.of("node", "--version"), 5);
    }
}
