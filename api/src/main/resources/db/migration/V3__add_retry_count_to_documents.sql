-- V3: Add retry_count to documents for dead-letter queue logic
ALTER TABLE documents ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
