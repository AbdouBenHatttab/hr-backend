ALTER TABLE authorization_requests
    ADD COLUMN IF NOT EXISTS equipment_type VARCHAR(255);
