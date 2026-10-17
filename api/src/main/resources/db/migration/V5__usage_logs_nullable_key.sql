-- Allow api_key_id to be NULL for JWT-authenticated requests
ALTER TABLE usage_logs ALTER COLUMN api_key_id DROP NOT NULL;

-- Add status_code column to track HTTP response code
ALTER TABLE usage_logs ADD COLUMN IF NOT EXISTS status_code INT DEFAULT 200;
