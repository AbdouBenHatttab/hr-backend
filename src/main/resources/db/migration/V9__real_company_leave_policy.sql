CREATE TABLE IF NOT EXISTS public_holidays (
    id BIGSERIAL PRIMARY KEY,
    holiday_date DATE NOT NULL,
    name VARCHAR(255) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    holiday_year INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    tentative BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(80) NOT NULL,
    source_key VARCHAR(300) NOT NULL,
    last_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_public_holiday_country_date_source UNIQUE (country_code, holiday_date, source),
    CONSTRAINT uk_public_holiday_source_key UNIQUE (source, source_key)
);

CREATE INDEX IF NOT EXISTS idx_public_holidays_country_year
    ON public_holidays(country_code, holiday_year);

CREATE INDEX IF NOT EXISTS idx_public_holidays_date_active
    ON public_holidays(holiday_date, active);

ALTER TABLE leave_policies
    ADD COLUMN IF NOT EXISTS accrual_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS monthly_accrual_days NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS carry_forward_cap_days NUMERIC(6,2) NOT NULL DEFAULT 0;

ALTER TABLE employee_leave_balances
    ALTER COLUMN allocated_days TYPE NUMERIC(6,2) USING allocated_days::NUMERIC(6,2),
    ALTER COLUMN reserved_days TYPE NUMERIC(6,2) USING reserved_days::NUMERIC(6,2),
    ALTER COLUMN used_days TYPE NUMERIC(6,2) USING used_days::NUMERIC(6,2);

ALTER TABLE employee_leave_balances
    ADD COLUMN IF NOT EXISTS carry_forward_days NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_accrued_month INTEGER NOT NULL DEFAULT 0;

UPDATE leave_policies
SET annual_allowance_days = 30,
    balance_managed = TRUE,
    accrual_managed = TRUE,
    monthly_accrual_days = 2.50,
    carry_forward_cap_days = 5.00,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE leave_type = 'ANNUAL';

UPDATE leave_policies
SET accrual_managed = FALSE,
    monthly_accrual_days = 0,
    carry_forward_cap_days = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE leave_type <> 'ANNUAL';

UPDATE employee_leave_balances b
SET allocated_days = LEAST(allocated_days, EXTRACT(MONTH FROM CURRENT_DATE)::NUMERIC * 2.50),
    last_accrued_month = CASE
        WHEN balance_year = EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER THEN EXTRACT(MONTH FROM CURRENT_DATE)::INTEGER
        WHEN balance_year < EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER THEN 12
        ELSE 0
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE leave_type = 'ANNUAL';
