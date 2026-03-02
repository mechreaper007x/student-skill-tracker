package com.skilltracker.student_skill_tracker.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DuelServiceTest {

    @Test
    void bloomLevelForRound_returnsCorrectValues() throws Exception {
        java.lang.reflect.Method method = DuelService.class.getDeclaredMethod("bloomLevelForRound", int.class,
                String.class);
        method.setAccessible(true);
        // Corrected constructor call with 7 null arguments
        DuelService service = new DuelService(null, null, null, null, null, null, null);

        assertEquals(6, method.invoke(service, 5, "CODING"));
        assertEquals(1, method.invoke(service, 1, "MCQ"));
        assertEquals(2, method.invoke(service, 2, "MEMORY"));
        assertEquals(3, method.invoke(service, 3, "PUZZLE"));
        assertEquals(4, method.invoke(service, 4, "PROBLEM_SOLVING"));
        assertEquals(5, method.invoke(service, 5, "UNKNOWN"));
    }
}