package com.ragforge.llm.dto;

public record OllamaResponse(
    String model,
    String response,
    boolean done
) {}
