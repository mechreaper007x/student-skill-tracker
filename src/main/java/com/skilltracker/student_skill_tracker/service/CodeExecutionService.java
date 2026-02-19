package com.skilltracker.student_skill_tracker.service;

import com.skilltracker.student_skill_tracker.compiler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CodeExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(CodeExecutionService.class);
    private static final int DEFAULT_TIMEOUT = 10;
    
    public CompilationResult executeCode(String language, String sourceCode, String input) {
        return executeCode(language, sourceCode, input, DEFAULT_TIMEOUT);
    }
    
    public CompilationResult executeCode(String language, String sourceCode, 
                                        String input, int timeoutSeconds) {
        logger.info("Executing code in language: {}", language);
        
        try {
            ProgrammingLanguageCompiler compiler = CompilerFactory.getCompiler(language);
            
            long startTime = System.currentTimeMillis();
            CompilationResult result = compiler.executeCode(sourceCode, input, timeoutSeconds);
            long executionTime = System.currentTimeMillis() - startTime;
            
            result.setLanguage(compiler.getLanguageName());
            result.setExecutionTime(executionTime + "ms");
            result.setTimestamp(LocalDateTime.now());
            
            logger.info("Code execution completed. Success: {}, Time: {}ms", 
                       result.isSuccess(), executionTime);
            
            return result;
        } catch (Exception e) {
            logger.error("Code execution error", e);
            return CompilationResult.builder()
                .success(false)
                .error("Error: " + e.getMessage())
                .language(language)
                .timestamp(LocalDateTime.now())
                .build();
        }
    }
    
    public List<CompilerInfo> getAvailableCompilers() {
        return CompilerFactory.getAvailableCompilers();
    }
    
    public boolean isLanguageSupported(String language) {
        return CompilerFactory.isLanguageSupported(language);
    }
}