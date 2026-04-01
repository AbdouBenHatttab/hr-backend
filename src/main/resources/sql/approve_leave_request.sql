-- SQL Script to approve a leave request for testing PDF generation
-- Execute this after submitting a leave request to test the PDF download

-- Update leave request to APPROVED status with both approvals
UPDATE leave_requests
SET
    team_leader_decision = 'APPROVED',
    hr_decision = 'APPROVED',
    status = 'APPROVED',
    approval_date = CURRENT_DATE,
    updated_at = NOW()
WHERE id = 1;

-- Optional: Check the updated record
SELECT * FROM leave_requests WHERE id = 1;

-- Optional: View all leave requests
SELECT
    id,
    user_id,
    leave_type,
    start_date,
    end_date,
    number_of_days,
    status,
    team_leader_decision,
    hr_decision,
    approval_date,
    created_at
FROM leave_requests
ORDER BY id DESC;

