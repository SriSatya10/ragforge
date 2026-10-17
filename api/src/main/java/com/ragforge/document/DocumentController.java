package com.ragforge.document;

import com.ragforge.auth.TenantPrincipal;
import com.ragforge.document.dto.DocumentStatusResponse;
import com.ragforge.document.dto.UploadResponse;
import com.ragforge.ingestion.IngestionProducer;
import com.ragforge.tenant.Tenant;
import com.ragforge.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Document upload and status endpoints.
 *
 * Upload flow:
 * 1. Validate file type (PDF or TXT only)
 * 2. Save file to disk (shared volume so the worker can access it)
 * 3. Create a DB record with status=PENDING
 * 4. Push a job to the Redis Stream
 * 5. Return 202 Accepted with the document ID
 *
 * The actual processing (parsing, chunking, embedding) is done by the
 * ingestion worker in Phase 5 — the upload endpoint returns immediately.
 */
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private static final List<String> ALLOWED_TYPES = List.of(
        "application/pdf",
        "text/plain"
    );

    private final DocumentRepository documentRepo;
    private final TenantRepository tenantRepo;
    private final IngestionProducer ingestionProducer;
    private final String uploadDir;

    public DocumentController(DocumentRepository documentRepo,
                              TenantRepository tenantRepo,
                              IngestionProducer ingestionProducer,
                              @Value("${app.upload-dir:/data/uploads}") String uploadDir) {
        this.documentRepo = documentRepo;
        this.tenantRepo = tenantRepo;
        this.ingestionProducer = ingestionProducer;
        this.uploadDir = uploadDir;
    }

    /**
     * Upload a document for processing.
     * Returns 202 (Accepted) because processing happens asynchronously.
     */
    @PostMapping
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal TenantPrincipal principal) {

        // --- Validate ---
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        String lowerName = filename != null ? filename.toLowerCase() : "";

        boolean isAllowedExt = lowerName.endsWith(".txt") || lowerName.endsWith(".pdf") ||
                               lowerName.endsWith(".md") || lowerName.endsWith(".markdown") ||
                               lowerName.endsWith(".json") || lowerName.endsWith(".csv") ||
                               lowerName.endsWith(".log");

        boolean isAllowedMime = contentType != null && 
                               (contentType.startsWith("text/") || 
                                contentType.equals("application/pdf") || 
                                contentType.equals("application/octet-stream") ||
                                contentType.equals("application/json"));

        if (!isAllowedExt && !isAllowedMime) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file format: " + filename + ". Supported: PDF, TXT, MD, CSV, JSON, LOG.");
        }

        Tenant tenant = tenantRepo.findById(principal.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        // --- Create DB record (PENDING) ---
        Document doc = new Document();
        doc.setTenant(tenant);
        doc.setFilename(file.getOriginalFilename());
        doc = documentRepo.save(doc);

        // --- Save file to disk ---
        // Path: /data/uploads/{tenantId}/{documentId}/{filename}
        // Tenant subdirectory provides filesystem-level isolation
        try {
            Path dir = Paths.get(uploadDir, tenant.getId().toString(), doc.getId().toString());
            Files.createDirectories(dir);
            Path filePath = dir.resolve(file.getOriginalFilename());
            file.transferTo(filePath.toFile());
            log.info("Saved file: {}", filePath);
        } catch (IOException e) {
            // Clean up the DB record if file save fails
            documentRepo.delete(doc);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to save file: " + e.getMessage());
        }

        // --- Push job to Redis Stream ---
        ingestionProducer.publishJob(doc.getId(), tenant.getId(), doc.getFilename());

        // --- Return 202 Accepted ---
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new UploadResponse(
                        doc.getId(),
                        doc.getFilename(),
                        doc.getStatus(),
                        doc.getCreatedAt()));
    }

    /**
     * Check the processing status of an uploaded document.
     * Always filtered by tenantId — a tenant cannot see another tenant's documents.
     */
    @GetMapping("/{id}")
    public DocumentStatusResponse getStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal TenantPrincipal principal) {

        Document doc = documentRepo.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document not found"));

        return new DocumentStatusResponse(
                doc.getId(),
                doc.getFilename(),
                doc.getStatus(),
                doc.getCreatedAt());
    }
}
