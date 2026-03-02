package com.skilltracker.student_skill_tracker.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompilerFactory {

    private static final Map<String, ProgrammingLanguageCompiler> COMPILERS = new HashMap<>();

    static {
        // --- PROACTIVE FIX: Offload to Piston API to bypass Render 0.1 CPU limit ---
        COMPILERS.put("java", new PistonCompilerProvider("java"));
        COMPILERS.put("python", new PistonCompilerProvider("python"));
        COMPILERS.put("cpp", new PistonCompilerProvider("cpp"));
        COMPILERS.put("c++", new PistonCompilerProvider("cpp"));
        COMPILERS.put("javascript", new PistonCompilerProvider("javascript"));
        COMPILERS.put("js", new PistonCompilerProvider("javascript"));
    }

    public static ProgrammingLanguageCompiler getCompiler(String language) {
        ProgrammingLanguageCompiler compiler = COMPILERS.get(language.toLowerCase());
        if (compiler == null) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        return compiler;
    }

    public static List<CompilerInfo> getAvailableCompilers() {
        List<CompilerInfo> available = new ArrayList<>();
        for (Map.Entry<String, ProgrammingLanguageCompiler> entry : COMPILERS.entrySet()) {
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

    public static boolean isLanguageSupported(String language) {
        ProgrammingLanguageCompiler compiler = COMPILERS.get(language.toLowerCase());
        return compiler != null && compiler.isLanguageAvailable();
    }
}