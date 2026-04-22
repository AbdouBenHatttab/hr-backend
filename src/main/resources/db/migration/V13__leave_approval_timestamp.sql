ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

UPDATE leave_requests
SET approved_at = updated_at
WHERE status = 'APPROVED'
  AND approved_at IS NULL
  AND updated_at IS NOT NULL;
