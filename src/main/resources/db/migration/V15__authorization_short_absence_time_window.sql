ALTER TABLE authorization_requests
    ADD COLUMN IF NOT EXISTS absence_date DATE;

ALTER TABLE authorization_requests
    ADD COLUMN IF NOT EXISTS from_time TIME;

ALTER TABLE authorization_requests
    ADD COLUMN IF NOT EXISTS to_time TIME;

UPDATE authorization_requests
SET absence_date = COALESCE(absence_date, start_date),
    end_date = COALESCE(end_date, start_date)
WHERE authorization_type = 'TIME_PERMISSION'
  AND start_date IS NOT NULL;
