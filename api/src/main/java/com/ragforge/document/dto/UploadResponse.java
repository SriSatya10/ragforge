package com.ragforge.document.dto;

import java.time.Instant;

/**
 * Returned on POST /documents (202 Accepted).
 * Gives the client the document ID to poll status with.
 */
public record UploadResponse(
    Long documentId,
    String filename,
    String status,
    Instant createdAt
) {}
