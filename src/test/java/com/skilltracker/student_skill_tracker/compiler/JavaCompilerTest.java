package com.skilltracker.student_skill_tracker.compiler;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JavaCompilerTest {

    private JavaCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new JavaCompiler();
        assumeTrue(
                ExecutionSandbox.isCommandAvailable(List.of("javac", "-version"), 5),
                "Skipping JavaCompiler tests because javac is not available in this environment.");
    }

    @Test
    void classOnlySolutionWithoutMain_returnsGuidanceInsteadOfError() {
        String source = """
                class Solution {
                    int add(int a, int b) {
                        return a + b;
                    }
                }
                """;

        CompilationResult result = compiler.executeCode(source, "", 5);

        assertTrue(result.isSuccess(), "Class-only Java solutions should not fail local run.");
        assertEquals("", result.getError());
        assertTrue(result.getOutput().contains("No main() method detected"));
    }

    @Test
    void packagePrivateClassWithMain_executesSuccessfully() {
        String source = """
                class Solution {
                    public static void main(String[] args) {
                        System.out.print("OK");
                    }
                }
                """;

        CompilationResult result = compiler.executeCode(source, "", 5);

        assertTrue(result.isSuccess(), "Package-private class with main should run.");
        assertEquals("OK", result.getOutput());
    }

    @Test
    void publicFinalClassWithMain_isRenamedAndExecutes() {
        String source = """
                public final class Solution {
                    public static void main(String[] args) {
                        System.out.print("FINAL");
                    }
                }
                """;

        CompilationResult result = compiler.executeCode(source, "", 5);

        assertTrue(result.isSuccess(), "public final class should be rewritten to generated filename and run.");
        assertEquals("FINAL", result.getOutput());
    }
}
