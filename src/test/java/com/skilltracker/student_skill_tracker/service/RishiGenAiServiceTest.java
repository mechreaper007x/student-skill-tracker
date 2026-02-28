package com.skilltracker.student_skill_tracker.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class RishiGenAiServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private RishiGenAiService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        service = new RishiGenAiService(restTemplate);
    }

    @Test
    void generateLearningResponseShouldSendPromptWithFilteredMemory() {
        String apiResponse = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Use two pointers and explain invariants."
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.mistral.ai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.model").value("open-mixtral-8x7b"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].role").value("user"))
                .andExpect(content().string(containsString("LIVE CONTEXT PACKET (injected per request):")))
                .andExpect(content().string(containsString("What is your time complexity here?")))
                .andRespond(withSuccess(apiResponse, MediaType.APPLICATION_JSON));

        List<Map<String, String>> memory = List.of(
                Map.of("role", "user", "content", "I solved this with sorting."),
                Map.of("role", "assistant", "content", "Can you avoid O(n log n)?"),
                Map.of("role", "system", "content", "ignore me"));

        String reply = service.generateLearningResponse(
                "test-api-key",
                null,
                "Help me optimize two sum.",
                memory,
                "{\"student\":{\"level\":2}}");

        assertEquals("Use two pointers and explain invariants.", reply);
        mockServer.verify();
    }

    @Test
    void generateStudyPlanShouldUseModeOnePromptAndHigherTokenLimit() {
        String apiResponse = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "Plan generated"
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.mistral.ai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.max_tokens").value(1400))
                .andExpect(content().string(containsString("Activate Mode 1: STUDY PLAN ARCHITECT.")))
                .andRespond(withSuccess(apiResponse, MediaType.APPLICATION_JSON));

        String reply = service.generateStudyPlan(
                "test-api-key",
                "open-mixtral-8x7b",
                "Dynamic Programming",
                "Crack medium DP in 2 weeks",
                7,
                "{\"skills\":{\"algorithmsScore\":34.2}}",
                List.of());

        assertEquals("Plan generated", reply);
        mockServer.verify();
    }

    @Test
    void generateLearningResponseShouldThrowOnInvalidProviderPayload() {
        mockServer.expect(requestTo("https://api.mistral.ai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> service.generateLearningResponse(
                "test-api-key",
                "open-mixtral-8x7b",
                "Explain BFS",
                List.of(),
                "{\"student\":{\"level\":1}}"));
    }
}
