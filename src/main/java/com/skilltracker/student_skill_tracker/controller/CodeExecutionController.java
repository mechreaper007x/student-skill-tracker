package com.skilltracker.student_skill_tracker.controller;

import com.skilltracker.student_skill_tracker.compiler.*;
import com.skilltracker.student_skill_tracker.service.CodeExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/code-execution")
@PreAuthorize("hasRole('USER')")
public class CodeExecutionController {
    
    @Autowired
    private CodeExecutionService codeExecutionService;
    
    @PostMapping("/execute")
    public ResponseEntity<?> executeCode(@RequestBody Map<String, Object> request) {
        try {
            String language = (String) request.get("language");
            String sourceCode = (String) request.get("sourceCode");
            String input = (String) request.getOrDefault("input", "");
            Integer timeout = (Integer) request.getOrDefault("timeoutSeconds", 10);
            
            if (language == null || sourceCode == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Language and sourceCode are required"));
            }
            
            if (!codeExecutionService.isLanguageSupported(language)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Language not supported or not installed: " + language));
            }
            
            CompilationResult result = codeExecutionService.executeCode(
                language, sourceCode, input, timeout
            );
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/compilers")
    public ResponseEntity<List<CompilerInfo>> getAvailableCompilers() {
        List<CompilerInfo> compilers = codeExecutionService.getAvailableCompilers();
        return ResponseEntity.ok(compilers);
    }
    
    @GetMapping("/check/{language}")
    public ResponseEntity<?> checkLanguageSupport(@PathVariable String language) {
        boolean supported = codeExecutionService.isLanguageSupported(language);
        return ResponseEntity.ok(Map.of(
            "language", language,
            "supported", supported
        ));
    }
}