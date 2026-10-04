package com.ragforge.query.dto;

/**
 * A citation represents a snippet of text retrieved from the database
 * that was used to answer the question.
 */
public record Citation(
    Long documentId,
    String filename,
    String content,
    double similarityScore
) {}
