package com.ragforge.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Pushes ingestion jobs to a Redis Stream.
 *
 * Why Redis Streams instead of a simple list/pub-sub?
 * - Consumer groups: multiple workers can share the load
 * - Acknowledgment: jobs aren't lost if a worker crashes
 * - Replay: we can re-process failed messages
 * - Dead-letter: unacknowledged messages can be moved to a DLQ (Phase 10)
 *
 * The stream key is "ingestion:jobs". Each message carries:
 *   documentId — which document to process
 *   tenantId   — for tenant isolation in the worker
 *   filename   — for logging/debugging
 */
@Service
public class IngestionProducer {

    private static final Logger log = LoggerFactory.getLogger(IngestionProducer.class);

    /** Redis Stream key where ingestion jobs are published */
    public static final String STREAM_KEY = "ingestion:jobs";

    private final StringRedisTemplate redisTemplate;

    public IngestionProducer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Publish an ingestion job to the Redis Stream.
     * Returns the Redis-generated message ID (useful for debugging).
     */
    public String publishJob(Long documentId, Long tenantId, String filename) {
        Map<String, String> fields = Map.of(
            "documentId", documentId.toString(),
            "tenantId", tenantId.toString(),
            "filename", filename
        );

        // XADD ingestion:jobs * documentId ... tenantId ... filename ...
        StringRecord record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
        RecordId recordId = redisTemplate.opsForStream().add(record);

        log.info("Published ingestion job: stream={}, recordId={}, documentId={}, tenantId={}",
                STREAM_KEY, recordId, documentId, tenantId);

        return recordId != null ? recordId.getValue() : null;
    }
}
