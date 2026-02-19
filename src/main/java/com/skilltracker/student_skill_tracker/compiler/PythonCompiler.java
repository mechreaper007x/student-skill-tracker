package com.skilltracker.student_skill_tracker.compiler;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

public class PythonCompiler implements ProgrammingLanguageCompiler {
    
    private static final String LANGUAGE_NAME = "Python";
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    
    @Override
    public CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds) {
        CompilationResult result = new CompilationResult();
        String uniqueId = UUID.randomUUID().toString();
        String fileName = "solution_" + uniqueId + ".py";
        String filePath = TEMP_DIR + File.separator + fileName;
        
        try {
            // Write Python code
            Files.write(Paths.get(filePath), sourceCode.getBytes());
            
            // Execute
            ProcessBuilder runBuilder = new ProcessBuilder("python3", filePath);
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
                result.setError("Runtime error (exit code: " + exitCode + ")");
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
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
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
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
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