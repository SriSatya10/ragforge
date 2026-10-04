package com.ragforge.llm.dto;

public record OllamaRequest(
    String model,
    String prompt,
    boolean stream
) {}
