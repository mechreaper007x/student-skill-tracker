package com.skilltracker.student_skill_tracker.compiler;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

public class JavaCompiler implements ProgrammingLanguageCompiler {
    
    private static final String LANGUAGE_NAME = "Java";
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    
    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        CompilationResult result = new CompilationResult();
        String uniqueId = UUID.randomUUID().toString();
        String className = "Solution_" + uniqueId.replace("-", "");
        String filePath = TEMP_DIR + File.separator + className + ".java";
        
        try {
            // Step 1: Replace class name
            String modifiedSource = sourceCode.replaceAll(
                "public\\s+class\\s+\\w+", 
                "public class " + className
            );
            
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
            
            // Step 4: Execute
            File tempFile = new File(filePath);
            String classPath = tempFile.getParent();
            
            ProcessBuilder runBuilder = new ProcessBuilder("java", "-cp", classPath, className);
            runBuilder.redirectErrorStream(true);
            Process runProcess = runBuilder.start();
            
            // Step 5: Send input
            if (input != null && !input.isEmpty()) {
                try (OutputStream os = runProcess.getOutputStream()) {
                    os.write(input.getBytes());
                    os.flush();
                }
            }
            
            // Step 6: Wait with timeout
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
            result.setOutput(output);
            if (exitCode != 0) {
                result.setError("Runtime error (exit code: " + exitCode + ")");
            }
            
        } catch (IOException | InterruptedException e) {
            result.setSuccess(false);
            result.setError("Exception: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(Paths.get(filePath));
                Files.deleteIfExists(Paths.get(TEMP_DIR + File.separator + className + ".class"));
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
            ProcessBuilder pb = new ProcessBuilder("java", "-version");
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
            ProcessBuilder pb = new ProcessBuilder("java", "-version");
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