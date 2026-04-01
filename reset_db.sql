-- ============================================================
-- HR-Nexus Database Clean Reset
-- Keeps ONLY the HR Manager account (alice_manager)
-- Run this in pgAdmin Query Tool on your hr_db database
-- ============================================================

-- Step 1: Disable FK constraints temporarily
SET session_replication_role = replica;

-- Step 2: Wipe all business data (order matters due to FKs)
TRUNCATE TABLE tasks                  RESTART IDENTITY CASCADE;
TRUNCATE TABLE projects               RESTART IDENTITY CASCADE;
TRUNCATE TABLE leave_requests         RESTART IDENTITY CASCADE;
TRUNCATE TABLE loan_requests          RESTART IDENTITY CASCADE;
TRUNCATE TABLE document_requests      RESTART IDENTITY CASCADE;
TRUNCATE TABLE authorization_requests RESTART IDENTITY CASCADE;
TRUNCATE TABLE login_history          RESTART IDENTITY CASCADE;

-- Step 3: Detach all users from teams
UPDATE users SET team_id = NULL;

-- Step 4: Delete teams
TRUNCATE TABLE teams RESTART IDENTITY CASCADE;

-- Step 5: Delete all users EXCEPT the HR manager
-- (replace 'alice_manager' with your actual HR username if different)
DELETE FROM users WHERE username != 'alice_manager';

-- Step 6: Delete orphaned persons (persons with no linked user)
DELETE FROM persons WHERE id NOT IN (
    SELECT person_id FROM users WHERE person_id IS NOT NULL
);

-- Step 7: Delete password reset tokens
TRUNCATE TABLE password_reset_tokens RESTART IDENTITY CASCADE;

-- Step 8: Re-enable FK constraints
SET session_replication_role = DEFAULT;

-- Step 9: Verify what remains
SELECT u.id, u.username, u.role, u.active,
       p.first_name, p.last_name, p.email
FROM users u
LEFT JOIN persons p ON u.person_id = p.id;
