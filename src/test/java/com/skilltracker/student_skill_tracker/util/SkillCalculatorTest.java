package com.skilltracker.student_skill_tracker.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCalculatorTest {

    private SkillCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SkillCalculator();
        ReflectionTestUtils.setField(calculator, "psDivisor", 500.0);
        ReflectionTestUtils.setField(calculator, "algoMediumWeight", 2.0);
        ReflectionTestUtils.setField(calculator, "algoHardWeight", 3.0);
        ReflectionTestUtils.setField(calculator, "algoDivisor", 1000.0);
        ReflectionTestUtils.setField(calculator, "dsEasyWeight", 1.0);
        ReflectionTestUtils.setField(calculator, "dsMediumWeight", 2.0);
        ReflectionTestUtils.setField(calculator, "dsDivisor", 500.0);
    }

    @Test
    void eqScore_zeroSubmissions_returnsHighDefault() {
        assertEquals(100.0, calculator.calculateEqScore(0, 0));
    }

    @Test
    void eqScore_impulsiveRecovery_returnsLow() {
        double score = calculator.calculateEqScore(5000L, 10); // 5 second recovery
        assertTrue(score < 50.0);
    }

    @Test
    void eqScore_patientRecovery_returnsHigh() {
        double score = calculator.calculateEqScore(60000L, 10); // 60 second recovery
        assertEquals(100.0, score);
    }

    @Test
    void reasoningScore_highPlanning_getsBonus() {
        double scoreWithBonus = calculator.calculateReasoningScore(5, 10, 65000L); // >1 min
        double scoreWithoutBonus = calculator.calculateReasoningScore(5, 10, 30000L); // <1 min
        assertTrue(scoreWithBonus > scoreWithoutBonus);
        assertEquals(60.0, scoreWithBonus);
        assertEquals(50.0, scoreWithoutBonus);
    }

    @Test
    void criticalThinkingScore_calculatesCorrectRatio() {
        double score = calculator.calculateCriticalThinkingScore(8, 10);
        assertEquals(80.0, score);
    }
}