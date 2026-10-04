-- V2: Add password_hash to tenants for JWT login
-- BCrypt hashes are 60 chars, but we use 255 for future flexibility
ALTER TABLE tenants ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '';

-- Add email column for login identifier (name is the company, email is the user)
ALTER TABLE tenants ADD COLUMN email VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE tenants ADD CONSTRAINT uq_tenants_email UNIQUE (email);
