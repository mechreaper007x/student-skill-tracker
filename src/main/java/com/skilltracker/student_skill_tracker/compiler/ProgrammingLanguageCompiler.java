package com.skilltracker.student_skill_tracker.compiler;

import java.util.Map;

/**
 * Interface for multi-language code compilation and execution
 */
public interface ProgrammingLanguageCompiler {
    
    /**
     * Compile and execute code
     * @param sourceCode The code to compile/execute
     * @param input Standard input for the program
     * @param timeoutSeconds Maximum execution time
     * @return Execution result with output or error
     */
    CompilationResult executeCode(String sourceCode, String input, int timeoutSeconds);
    
    /**
     * Get the programming language this compiler supports
     */
    String getLanguageName();
    
    /**
     * Check if language is installed/available
     */
    boolean isLanguageAvailable();
    
    /**
     * Get version of the installed compiler/interpreter
     */
    String getLanguageVersion();
}