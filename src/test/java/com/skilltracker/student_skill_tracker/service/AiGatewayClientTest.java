package com.skilltracker.student_skill_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AiGatewayClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AiGatewayClient gatewayClient;

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void sendsEveryRequestThroughOpenAiCompatibleChatCompletions() {
        String endpoint = "https://gateway.example/v1/chat/completions";
        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "Gateway response"))));

        when(restTemplate.exchange(
                eq(endpoint), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(responseBody));

        String response = gatewayClient.complete(
                "test-key",
                "provider/model-id",
                "https://gateway.example/v1",
                List.of(Map.of("role", "user", "content", "Hello")),
                0.2,
                500);

        assertEquals("Gateway response", response);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(endpoint), eq(HttpMethod.POST), requestCaptor.capture(), eq(Map.class));

        HttpEntity<?> request = requestCaptor.getValue();
        assertNotNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("Bearer test-key", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) request.getBody();
        assertNotNull(body);
        assertEquals("provider/model-id", body.get("model"));
        assertEquals(List.of(Map.of("role", "user", "content", "Hello")), body.get("messages"));
    }
}
