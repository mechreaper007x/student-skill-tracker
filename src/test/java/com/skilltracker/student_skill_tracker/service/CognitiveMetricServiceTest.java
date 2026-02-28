package com.skilltracker.student_skill_tracker.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CognitiveMetricServiceTest {

    // Using reflection or a wrapper since inferThinkingStyle is private
    // We can test the logic directly if we make it package-private, or use ReflectionTestUtils.
    // For simplicity in this test, we test the public interface behavior or use a wrapper.
    
    @Test
    void inferThinkingStyle_bothCorrect_returnsSystem1Dominant() throws Exception {
        java.lang.reflect.Method method = CognitiveMetricService.class.getDeclaredMethod("inferThinkingStyle", boolean.class, boolean.class);
        method.setAccessible(true);
        CognitiveMetricService service = new CognitiveMetricService(null, null, null);
        
        String result = (String) method.invoke(service, true, true);
        assertEquals("system1_dominant", result);
    }

    @Test
    void inferThinkingStyle_onlyRoundBCorrect_returnsSystem2Deliberate() throws Exception {
        java.lang.reflect.Method method = CognitiveMetricService.class.getDeclaredMethod("inferThinkingStyle", boolean.class, boolean.class);
        method.setAccessible(true);
        CognitiveMetricService service = new CognitiveMetricService(null, null, null);
        
        String result = (String) method.invoke(service, false, true);
        assertEquals("system2_deliberate", result);
    }

    @Test
    void inferThinkingStyle_onlyRoundACorrect_returnsSystem1Dominant() throws Exception {
        java.lang.reflect.Method method = CognitiveMetricService.class.getDeclaredMethod("inferThinkingStyle", boolean.class, boolean.class);
        method.setAccessible(true);
        CognitiveMetricService service = new CognitiveMetricService(null, null, null);
        
        String result = (String) method.invoke(service, true, false);
        assertEquals("system1_dominant", result);
    }

    @Test
    void inferThinkingStyle_neitherCorrect_returnsBalancedNeedsPractice() throws Exception {
        java.lang.reflect.Method method = CognitiveMetricService.class.getDeclaredMethod("inferThinkingStyle", boolean.class, boolean.class);
        method.setAccessible(true);
        CognitiveMetricService service = new CognitiveMetricService(null, null, null);
        
        String result = (String) method.invoke(service, false, false);
        assertEquals("balanced_needs_practice", result);
    }
}