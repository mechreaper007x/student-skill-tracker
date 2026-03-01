package com.skilltracker.student_skill_tracker.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shared sandbox runner for local compiler executions.
 *
 * <p>
 * This is process-level isolation (per-run workspace + hardened env + timeout),
 * not full VM/container isolation.
 * </p>
 */
final class ExecutionSandbox {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final int MAX_SOURCE_SIZE_BYTES = 200_000;

    private ExecutionSandbox() {
    }

    static Path createWorkspace(String languageTag) throws IOException {
        String safeTag = languageTag == null ? "code" : languageTag.replaceAll("[^a-zA-Z0-9_-]", "");
        return Files.createTempDirectory("compiler-sandbox-" + safeTag + "-");
    }

    static void validateSourceSize(String sourceCode) {
        if (sourceCode == null) {
            return;
        }
        int size = sourceCode.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_SOURCE_SIZE_BYTES) {
            throw new IllegalArgumentException("Source code too large. Limit is " + MAX_SOURCE_SIZE_BYTES + " bytes.");
        }
    }

    static ProcessResult run(Path workspace, List<String> command, String stdin, int timeoutSeconds)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workspace.toFile());
        builder.redirectErrorStream(true);
        hardenEnvironment(builder.environment(), workspace);

        Process process = builder.start();
        try {
            if (stdin != null && !stdin.isEmpty()) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new ProcessResult(-1, "Execution timeout (" + timeoutSeconds + " seconds)", true);
            }

            String output = readStream(process.getInputStream());
            return new ProcessResult(process.exitValue(), output, false);
        } finally {
            process.getOutputStream().close();
        }
    }

    static boolean isCommandAvailable(List<String> command, int timeoutSeconds) {
        try {
            Path workspace = createWorkspace("check");
            try {
                ProcessResult result = run(workspace, command, "", timeoutSeconds);
                return !result.timedOut() && result.exitCode() == 0;
            } finally {
                deleteWorkspace(workspace);
            }
        } catch (Exception e) {
            return false;
        }
    }

    static String readCommandOutput(List<String> command, int timeoutSeconds) {
        try {
            Path workspace = createWorkspace("version");
            try {
                ProcessResult result = run(workspace, command, "", timeoutSeconds);
                if (result.timedOut()) {
                    return "Unknown";
                }
                return result.output();
            } finally {
                deleteWorkspace(workspace);
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }

    static void deleteWorkspace(Path workspace) {
        if (workspace == null) {
            return;
        }

        try {
            Files.walk(workspace)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void hardenEnvironment(Map<String, String> env, Path workspace) {
        String pathValue = env.getOrDefault("PATH", System.getenv("PATH"));
        String systemRoot = System.getenv("SystemRoot");
        String comSpec = System.getenv("ComSpec");

        env.clear();
        if (pathValue != null && !pathValue.isBlank()) {
            env.put("PATH", pathValue);
        }

        if (IS_WINDOWS) {
            if (systemRoot != null && !systemRoot.isBlank()) {
                env.put("SystemRoot", systemRoot);
            }
            if (comSpec != null && !comSpec.isBlank()) {
                env.put("ComSpec", comSpec);
            }
            env.put("TEMP", workspace.toString());
            env.put("TMP", workspace.toString());
        } else {
            env.put("HOME", workspace.toString());
            env.put("TMPDIR", workspace.toString());
            env.put("LANG", "C.UTF-8");
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    record ProcessResult(int exitCode, String output, boolean timedOut) {
    }
}
