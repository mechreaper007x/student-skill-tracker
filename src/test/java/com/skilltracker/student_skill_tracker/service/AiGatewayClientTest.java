package com.skilltracker.student_skill_tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
        String endpoint = "https://1.1.1.1/v1/chat/completions";
        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "Gateway response"))));

        when(restTemplate.exchange(
                eq(endpoint), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(responseBody));

        String response = gatewayClient.complete(
                "test-key",
                "provider/model-id",
                "https://1.1.1.1/v1",
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
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void fetchesAllModelIdsFromTheConfiguredApi() {
        String endpoint = "https://1.1.1.1/v1/models";
        Map<String, Object> responseBody = Map.of(
                "data", List.of(
                        Map.of("id", "model-alpha"),
                        Map.of("id", "model-beta"),
                        Map.of("id", "model-gamma")));

        when(restTemplate.exchange(
                eq(endpoint), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(responseBody));

        List<String> models = gatewayClient.listModels("test-key", "https://1.1.1.1/v1");

        assertEquals(List.of("model-alpha", "model-beta", "model-gamma"), models);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(endpoint), eq(HttpMethod.GET), requestCaptor.capture(), eq(Map.class));
        assertEquals("Bearer test-key",
                requestCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void followsSameOriginPaginationLinksAndDeduplicatesModelIds() {
        String firstPage = "https://1.1.1.1/v1/models";
        String secondPage = "https://1.1.1.1/v1/models?offset=3";
        Map<String, Object> firstResponse = Map.of(
                "data", List.of(Map.of("id", "model-alpha"), Map.of("id", "model-beta")),
                "links", Map.of("next", "/v1/models?offset=3"));
        Map<String, Object> secondResponse = Map.of(
                "data", List.of(Map.of("id", "model-beta"), Map.of("id", "model-gamma")));

        when(restTemplate.exchange(
                eq(firstPage), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(firstResponse));
        when(restTemplate.exchange(
                eq(secondPage), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(secondResponse));

        assertEquals(List.of("model-alpha", "model-beta", "model-gamma"),
                gatewayClient.listModels("test-key", "https://1.1.1.1/v1"));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void usesOpenRouterUserFilteredModelCatalogWhenRequested() {
        String endpoint = "https://1.1.1.1/v1/models/user?offset=0&limit=1000";
        Map<String, Object> responseBody = Map.of(
                "data", List.of(Map.of("id", "provider/model-allowed")));

        when(restTemplate.exchange(
                eq(endpoint), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(responseBody));

        assertEquals(List.of("provider/model-allowed"),
                gatewayClient.listModels("test-key", "https://1.1.1.1/v1", true));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void paginatesOpenRouterUserModelCatalogInBatchesOfOneThousand() {
        String firstPage = "https://1.1.1.1/v1/models/user?offset=0&limit=1000";
        String secondPage = "https://1.1.1.1/v1/models/user?offset=1000&limit=1000";
        List<Map<String, String>> firstBatch = IntStream.range(0, 1000)
                .mapToObj(i -> Map.of("id", "provider/model-" + i))
                .toList();
        Map<String, Object> firstResponse = Map.of("data", firstBatch);
        Map<String, Object> secondResponse = Map.of(
                "data", List.of(Map.of("id", "provider/model-1000"), Map.of("id", "provider/model-1001")));

        when(restTemplate.exchange(
                eq(firstPage), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(firstResponse));
        when(restTemplate.exchange(
                eq(secondPage), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(secondResponse));

        List<String> models = gatewayClient.listModels("test-key", "https://1.1.1.1/v1", true);

        assertEquals(1002, models.size());
        assertEquals("provider/model-0", models.get(0));
        assertEquals("provider/model-1001", models.get(1001));
    }

}
