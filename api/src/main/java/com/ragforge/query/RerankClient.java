package com.ragforge.query;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client for the ML sidecar /rerank endpoint.
 */
@Component
public class RerankClient {

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public RerankClient(RestTemplateBuilder builder,
                        @Value("${app.ml-service-url}") String mlServiceUrl) {
        this.restTemplate = builder.build();
        this.mlServiceUrl = mlServiceUrl;
    }

    /**
     * Reranks a list of candidate documents against a query using a Cross-Encoder.
     * @return List of indices of the original documents array, sorted by relevance.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> rerank(String query, List<String> documents, int topK) {
        if (documents.isEmpty()) return List.of();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "query", query,
                "documents", documents,
                "top_k", topK
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        Map<String, Object> response = restTemplate.postForObject(
                mlServiceUrl + "/rerank",
                request,
                Map.class
        );

        if (response == null || !response.containsKey("results")) {
            throw new RuntimeException("Invalid response from ML sidecar for reranking");
        }

        return (List<Map<String, Object>>) response.get("results");
    }
}
