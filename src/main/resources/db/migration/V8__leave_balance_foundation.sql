CREATE TABLE IF NOT EXISTS leave_policies (
    id BIGSERIAL PRIMARY KEY,
    leave_type VARCHAR(40) NOT NULL,
    annual_allowance_days INTEGER NOT NULL DEFAULT 0,
    balance_managed BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_leave_policy_type UNIQUE (leave_type)
);

CREATE TABLE IF NOT EXISTS employee_leave_balances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    leave_type VARCHAR(40) NOT NULL,
    balance_year INTEGER NOT NULL,
    allocated_days INTEGER NOT NULL DEFAULT 0,
    reserved_days INTEGER NOT NULL DEFAULT 0,
    used_days INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_employee_leave_balance_year_type UNIQUE (user_id, leave_type, balance_year),
    CONSTRAINT fk_employee_leave_balances_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_employee_leave_balances_user_year
    ON employee_leave_balances(user_id, balance_year);

INSERT INTO leave_policies (leave_type, annual_allowance_days, balance_managed, active)
VALUES
    ('ANNUAL', 30, TRUE, TRUE),
    ('SICK', 10, TRUE, TRUE),
    ('MATERNITY', 60, TRUE, TRUE),
    ('PATERNITY', 3, TRUE, TRUE),
    ('UNPAID', 0, FALSE, TRUE),
    ('OTHER', 0, FALSE, TRUE)
ON CONFLICT (leave_type) DO UPDATE SET
    annual_allowance_days = EXCLUDED.annual_allowance_days,
    balance_managed = EXCLUDED.balance_managed,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO employee_leave_balances (
    user_id,
    leave_type,
    balance_year,
    allocated_days,
    reserved_days,
    used_days
)
SELECT
    lr.user_id,
    lr.leave_type,
    EXTRACT(YEAR FROM lr.start_date)::INTEGER AS balance_year,
    lp.annual_allowance_days,
    COALESCE(SUM(CASE WHEN lr.status = 'PENDING' THEN lr.number_of_days ELSE 0 END), 0) AS reserved_days,
    COALESCE(SUM(CASE WHEN lr.status = 'APPROVED' THEN lr.number_of_days ELSE 0 END), 0) AS used_days
FROM leave_requests lr
JOIN leave_policies lp ON lp.leave_type = lr.leave_type
WHERE lp.balance_managed = TRUE
  AND lp.active = TRUE
  AND lr.status IN ('PENDING', 'APPROVED')
GROUP BY lr.user_id, lr.leave_type, EXTRACT(YEAR FROM lr.start_date)::INTEGER, lp.annual_allowance_days
ON CONFLICT (user_id, leave_type, balance_year) DO UPDATE SET
    allocated_days = EXCLUDED.allocated_days,
    reserved_days = EXCLUDED.reserved_days,
    used_days = EXCLUDED.used_days,
    updated_at = CURRENT_TIMESTAMP;
