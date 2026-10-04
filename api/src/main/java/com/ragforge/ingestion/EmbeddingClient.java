package com.ragforge.ingestion;

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
 * Client for the Python ML sidecar.
 * Calls /embed to convert text chunks into vectors.
 */
@Component
public class EmbeddingClient {

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public EmbeddingClient(RestTemplateBuilder builder,
                           @Value("${app.ml-service-url}") String mlServiceUrl) {
        this.restTemplate = builder.build();
        this.mlServiceUrl = mlServiceUrl;
    }

    /**
     * Batch embeds a list of texts.
     * @return List of vector arrays, in the same order as the input texts.
     */
    @SuppressWarnings("unchecked")
    public List<List<Double>> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Map matches the EmbedRequest pydantic model in FastAPI
        Map<String, Object> body = Map.of("texts", texts);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // Make the POST request
        Map<String, Object> response = restTemplate.postForObject(
                mlServiceUrl + "/embed",
                request,
                Map.class
        );

        if (response == null || !response.containsKey("embeddings")) {
            throw new RuntimeException("Invalid response from ML sidecar");
        }

        return (List<List<Double>>) response.get("embeddings");
    }
}
