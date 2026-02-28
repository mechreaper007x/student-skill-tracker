package com.skilltracker.student_skill_tracker.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompilerFactory {

    private static final Map<String, ProgrammingLanguageCompiler> COMPILERS = new HashMap<>();

    static {
        COMPILERS.put("java", new JavaCompiler());
        COMPILERS.put("python", new PythonCompiler());
        COMPILERS.put("cpp", new CppCompiler());
        COMPILERS.put("c++", new CppCompiler());
        COMPILERS.put("javascript", new JavaScriptCompiler());
        COMPILERS.put("js", new JavaScriptCompiler());
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