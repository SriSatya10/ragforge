package com.ragforge.query.dto;

import java.util.List;

/**
 * The final response returned to the user for a query.
 */
public record QueryResponse(
    String answer,
    List<Citation> citations,
    boolean cacheHit
) {}
