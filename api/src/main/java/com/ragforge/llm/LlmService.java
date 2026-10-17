package com.ragforge.llm;

import com.ragforge.llm.dto.OllamaRequest;
import com.ragforge.llm.dto.OllamaResponse;
import com.ragforge.query.dto.Citation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service to call Ollama LLM and generate answers using RAG citations.
 */
@Service
public class LlmService {

    private final RestTemplate restTemplate;
    private final String ollamaBaseUrl;
    private final String modelName;

    public LlmService(RestTemplateBuilder builder,
                      @Value("${app.ollama-base-url}") String ollamaBaseUrl,
                      @Value("${app.ollama-model:phi3}") String modelName) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.modelName = modelName;
    }

    /**
     * Generates an answer to the question based ONLY on the provided citations.
     */
    public String generateAnswer(String question, List<Citation> citations) {
        if (citations.isEmpty()) {
            return "I couldn't find any relevant information in your documents to answer this question.";
        }

        String context = citations.stream()
                .map(c -> "Source File: " + c.filename() + "\nContext: " + c.content())
                .collect(Collectors.joining("\n\n"));

        String prompt = """
            You are a helpful assistant. Answer the user's question using ONLY the provided context below.
            If the context does not contain the answer, say "I don't know based on the provided documents."
            Do not use outside knowledge. Cite the source files if possible.
            
            Context:
            %s
            
            Question: %s
            
            Answer:
            """.formatted(context, question);

        OllamaRequest request = new OllamaRequest(modelName, prompt, false);

        try {
            OllamaResponse response = restTemplate.postForObject(
                    ollamaBaseUrl + "/api/generate",
                    request,
                    OllamaResponse.class
            );

            return response != null ? response.response() : "Error: Empty response from LLM.";
        } catch (Exception e) {
            return "Error calling LLM: " + e.getMessage();
        }
    }
}
