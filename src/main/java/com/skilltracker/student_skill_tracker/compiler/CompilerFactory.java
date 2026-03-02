package com.skilltracker.student_skill_tracker.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class CompilerFactory {

    @Value("${piston.api.url:https://emkc.org/api/v2/piston/execute}")
    private String pistonUrl;

    @Value("${piston.api.enabled:true}")
    private boolean pistonEnabled;

    private final Map<String, ProgrammingLanguageCompiler> compilers = new HashMap<>();

    @PostConstruct
    public void init() {
        if (pistonEnabled) {
            // PROACTIVE FIX: Use remote Piston API configured in application.properties
            compilers.put("java", new PistonCompilerProvider("java", pistonUrl));
            compilers.put("python", new PistonCompilerProvider("python", pistonUrl));
            compilers.put("cpp", new PistonCompilerProvider("cpp", pistonUrl));
            compilers.put("c++", new PistonCompilerProvider("cpp", pistonUrl));
            compilers.put("javascript", new PistonCompilerProvider("javascript", pistonUrl));
            compilers.put("js", new PistonCompilerProvider("javascript", pistonUrl));
        } else {
            // Fallback to local if disabled (not recommended for Render)
            compilers.put("java", new JavaCompiler());
            compilers.put("python", new PythonCompiler());
            compilers.put("cpp", new CppCompiler());
            compilers.put("c++", new CppCompiler());
            compilers.put("javascript", new JavaScriptCompiler());
            compilers.put("js", new JavaScriptCompiler());
        }
    }

    public ProgrammingLanguageCompiler getCompiler(String language) {
        ProgrammingLanguageCompiler compiler = compilers.get(language.toLowerCase());
        if (compiler == null) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        return compiler;
    }

    public List<CompilerInfo> getAvailableCompilers() {
        List<CompilerInfo> available = new ArrayList<>();
        for (Map.Entry<String, ProgrammingLanguageCompiler> entry : compilers.entrySet()) {
            ProgrammingLanguageCompiler compiler = entry.getValue();
            if (compiler.isLanguageAvailable()) {
                available.add(new CompilerInfo(
                        compiler.getLanguageName(),
                        compiler.getLanguageVersion(),
                        entry.getKey()));
            }
        }
        return available;
    }

    public boolean isLanguageSupported(String language) {
        ProgrammingLanguageCompiler compiler = compilers.get(language.toLowerCase());
        return compiler != null && compiler.isLanguageAvailable();
    }
}