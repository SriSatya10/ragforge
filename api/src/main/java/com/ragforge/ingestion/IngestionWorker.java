package com.ragforge.ingestion;

import com.ragforge.document.Document;
import com.ragforge.document.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Polls the Redis Stream for new ingestion jobs and processes them.
 */
@Service
public class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final StringRedisTemplate redisTemplate;
    private final DocumentRepository documentRepo;
    private final DocumentParser parser;
    private final TextChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final ChunkStore chunkStore;
    private final String uploadDir;

    public IngestionWorker(StringRedisTemplate redisTemplate,
                           DocumentRepository documentRepo,
                           DocumentParser parser,
                           TextChunker chunker,
                           EmbeddingClient embeddingClient,
                           ChunkStore chunkStore,
                           @Value("${app.upload-dir:/data/uploads}") String uploadDir) {
        this.redisTemplate = redisTemplate;
        this.documentRepo = documentRepo;
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.chunkStore = chunkStore;
        this.uploadDir = uploadDir;
        
        createConsumerGroup();
    }

    /** Create the Redis Stream and Consumer Group if they don't exist. */
    private void createConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(IngestionProducer.STREAM_KEY, "ingestion-group");
            log.info("Created consumer group 'ingestion-group'");
        } catch (Exception e) {
            // Group already exists, ignore
        }
    }

    /**
     * Poll the stream every 2 seconds.
     * In a production app, you might use StreamMessageListenerContainer for reactive
     * listening, but polling is simpler and perfectly fine for background jobs.
     */
    @Scheduled(fixedDelay = 2000)
    public void pollJobs() {
        // Read 1 message, wait up to 1 second
        @SuppressWarnings("unchecked")
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                org.springframework.data.redis.connection.stream.Consumer.from("ingestion-group", "worker-1"),
                org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(1).block(Duration.ofMillis(1000)),
                StreamOffset.create(IngestionProducer.STREAM_KEY, ReadOffset.lastConsumed())
        );

        if (messages == null || messages.isEmpty()) return;

        for (MapRecord<String, Object, Object> message : messages) {
            processMessage(message);
        }
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        Map<Object, Object> fields = message.getValue();
        Long documentId = Long.valueOf((String) fields.get("documentId"));
        Long tenantId = Long.valueOf((String) fields.get("tenantId"));
        String filename = (String) fields.get("filename");
        String messageId = message.getId().getValue();

        log.info("Processing job: docId={}, tenantId={}", documentId, tenantId);

        Document doc = documentRepo.findByIdAndTenantId(documentId, tenantId).orElse(null);
        if (doc == null) {
            log.warn("Document {} not found, acking and skipping", documentId);
            ackMessage(messageId);
            return;
        }

        try {
            // 1. Update status to PROCESSING
            doc.setStatus("PROCESSING");
            documentRepo.save(doc);

            // 2. Parse file
            Path filePath = Paths.get(uploadDir, tenantId.toString(), documentId.toString(), filename);
            String rawText = parser.parse(filePath, filename);

            // 3. Chunk
            List<String> chunks = chunker.chunk(rawText);
            log.info("Created {} chunks for doc {}", chunks.size(), documentId);

            // 4. Embed (we batch the whole document for simplicity here, 
            // but in a massive doc you'd batch in sets of e.g. 50 chunks)
            List<List<Double>> embeddings = embeddingClient.embed(chunks);

            // 5. Save to DB
            chunkStore.saveAll(documentId, tenantId, chunks, embeddings);

            // 6. Mark DONE and ACK
            doc.setStatus("DONE");
            documentRepo.save(doc);
            ackMessage(messageId);
            
            log.info("Successfully ingested document {}", documentId);

        } catch (Exception e) {
            log.error("Failed to process doc {}: {}", documentId, e.getMessage(), e);
            
            doc.setRetryCount(doc.getRetryCount() + 1);
            
            if (doc.getRetryCount() >= 3) {
                // Max retries reached: mark FAILED, push to DLQ, and ACK
                log.error("Max retries reached for doc {}. Moving to DLQ.", documentId);
                doc.setStatus("FAILED");
                documentRepo.save(doc);
                
                redisTemplate.opsForStream().add(
                    org.springframework.data.redis.connection.stream.StreamRecords.string(
                        (Map<String, String>) (Map<?, ?>) fields
                    ).withStreamKey(IngestionProducer.DLQ_STREAM_KEY)
                );
            } else {
                // Will retry: keep status PENDING/PROCESSING, push back to main stream, and ACK current message
                log.info("Will retry doc {}. Retry count: {}", documentId, doc.getRetryCount());
                doc.setStatus("PENDING");
                documentRepo.save(doc);
                
                redisTemplate.opsForStream().add(
                    org.springframework.data.redis.connection.stream.StreamRecords.string(
                        (Map<String, String>) (Map<?, ?>) fields
                    ).withStreamKey(IngestionProducer.STREAM_KEY)
                );
            }
            
            // Always ACK the current message ID so it doesn't get stuck in the consumer group PEL
            ackMessage(messageId);
        }
    }

    private void ackMessage(String messageId) {
        redisTemplate.opsForStream().acknowledge(IngestionProducer.STREAM_KEY, "ingestion-group", messageId);
    }
}
