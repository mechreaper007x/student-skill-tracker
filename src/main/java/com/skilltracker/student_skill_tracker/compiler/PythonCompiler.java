package com.skilltracker.student_skill_tracker.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PythonCompiler implements ProgrammingLanguageCompiler {

    private static final String LANGUAGE_NAME = "Python";
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
        Path workspace = null;

        try {
            ExecutionSandbox.validateSourceSize(sourceCode);
            workspace = ExecutionSandbox.createWorkspace("python");
            Path sourceFile = workspace.resolve("solution.py");
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

            ExecutionSandbox.ProcessResult runResult = ExecutionSandbox.run(
                    workspace,
                    List.of(PYTHON_CMD, "-I", "-S", "-B", sourceFile.toString()),
                    input,
                    timeoutSeconds);

            if (runResult.timedOut()) {
                result.setSuccess(false);
                result.setError(runResult.output());
                return result;
            }

            String output = runResult.output();
            int exitCode = runResult.exitCode();

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
        return ExecutionSandbox.isCommandAvailable(List.of(PYTHON_CMD, "--version"), 5);
    }

    @Override
    public String getLanguageVersion() {
        return ExecutionSandbox.readCommandOutput(List.of(PYTHON_CMD, "--version"), 5);
    }
}
