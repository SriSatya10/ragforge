package com.ragforge.document.dto;

import java.time.Instant;

/**
 * Returned on GET /documents/{id}.
 * Shows the current processing status of an uploaded document.
 */
public record DocumentStatusResponse(
    Long documentId,
    String filename,
    String status,
    Instant createdAt
) {}
