ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP;

UPDATE leave_requests
SET rejected_at = updated_at
WHERE status = 'REJECTED'
  AND rejected_at IS NULL
  AND updated_at IS NOT NULL;
