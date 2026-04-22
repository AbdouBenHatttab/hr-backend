ALTER TABLE loan_requests
    ADD COLUMN IF NOT EXISTS approved_at timestamp,
    ADD COLUMN IF NOT EXISTS rejected_at timestamp,
    ADD COLUMN IF NOT EXISTS hr_decision_reason varchar(1000),
    ADD COLUMN IF NOT EXISTS hr_decision_stage varchar(255),
    ADD COLUMN IF NOT EXISTS meeting_scheduled_by varchar(255),
    ADD COLUMN IF NOT EXISTS meeting_scheduled_at timestamp;

UPDATE loan_requests
SET approved_at = processed_at
WHERE status = 'APPROVED'
  AND approved_at IS NULL
  AND processed_at IS NOT NULL;

UPDATE loan_requests
SET rejected_at = processed_at,
    hr_decision_reason = COALESCE(NULLIF(hr_note, ''), hr_decision_reason),
    hr_decision_stage = COALESCE(hr_decision_stage, 'BEFORE_MEETING')
WHERE status = 'REJECTED'
  AND rejected_at IS NULL
  AND processed_at IS NOT NULL;
