package com.ragforge.query.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record QueryRequest(
    @NotBlank(message = "Question cannot be empty")
    String question,

    @Min(1) @Max(20)
    Integer topK
) {
    public QueryRequest {
        if (topK == null) topK = 5; // Default to top 5 chunks
    }
}
