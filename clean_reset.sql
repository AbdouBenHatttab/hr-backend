-- ============================================================
-- HR-Nexus Clean Reset — Run in pgAdmin Query Tool on hr_db
-- Deletes ALL app data except alice_manager (HR)
-- Does NOT touch Keycloak tables
-- ============================================================

-- Disable FK checks
SET session_replication_role = replica;

-- 1. Wipe all request/business tables
DELETE FROM tasks;
DELETE FROM projects;
DELETE FROM leave_requests;
DELETE FROM loan_requests;
DELETE FROM document_requests;
DELETE FROM authorization_requests;
DELETE FROM login_history;

-- 2. Remove team assignments from all users
UPDATE users SET team_id = NULL WHERE team_id IS NOT NULL;

-- 3. Delete all teams
DELETE FROM teams;

-- 4. Delete all users except alice_manager
DELETE FROM users WHERE username != 'alice_manager';

-- 5. Delete orphaned persons (no user linked)
DELETE FROM persons
WHERE id NOT IN (
    SELECT person_id FROM users WHERE person_id IS NOT NULL
);

-- 6. Reset sequences so IDs start from 1 again
ALTER SEQUENCE tasks_id_seq                  RESTART WITH 1;
ALTER SEQUENCE projects_id_seq               RESTART WITH 1;
ALTER SEQUENCE leave_requests_id_seq         RESTART WITH 1;
ALTER SEQUENCE loan_requests_id_seq          RESTART WITH 1;
ALTER SEQUENCE document_requests_id_seq      RESTART WITH 1;
ALTER SEQUENCE authorization_requests_id_seq RESTART WITH 1;
ALTER SEQUENCE login_history_id_seq          RESTART WITH 1;
ALTER SEQUENCE teams_id_seq                  RESTART WITH 1;

-- 7. Re-enable FK checks
SET session_replication_role = DEFAULT;

-- 8. Confirm result — should show only alice_manager
SELECT u.id, u.username, u.role, u.active,
       p.first_name, p.last_name, p.email
FROM users u
LEFT JOIN persons p ON u.person_id = p.id
ORDER BY u.id;
