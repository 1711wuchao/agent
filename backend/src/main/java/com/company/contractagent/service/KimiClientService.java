package com.company.contractagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KimiClientService {
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public KimiClientService(
            @Value("${kimi.api-base:https://api.moonshot.cn/v1}") String apiBase,
            @Value("${kimi.api-key:}") String apiKey,
            @Value("${kimi.model:moonshot-v1-8k}") String model,
            @Value("${kimi.timeout-ms:30000}") int timeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(apiBase)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, apiKey == null || apiKey.isBlank() ? "" : "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        body.put("temperature", 0.2);
        return restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> chatCompletion(List<Map<String, Object>> messages) {
        return chatCompletion(messages, List.of());
    }
}
