ALTER TABLE loan_requests
    ADD COLUMN IF NOT EXISTS approved_amount_justification VARCHAR(1000);
