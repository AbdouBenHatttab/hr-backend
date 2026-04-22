ALTER TABLE loan_requests
    ADD COLUMN IF NOT EXISTS cancellation_reason varchar(1000),
    ADD COLUMN IF NOT EXISTS canceled_by varchar(255),
    ADD COLUMN IF NOT EXISTS canceled_at timestamp;

DO $$
DECLARE
    status_constraint RECORD;
BEGIN
    IF to_regclass('public.loan_requests') IS NOT NULL THEN
        FOR status_constraint IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
            WHERE n.nspname = 'public'
              AND t.relname = 'loan_requests'
              AND a.attname = 'status'
              AND c.contype = 'c'
        LOOP
            EXECUTE format('ALTER TABLE public.loan_requests DROP CONSTRAINT IF EXISTS %I', status_constraint.conname);
        END LOOP;

        ALTER TABLE public.loan_requests
            ADD CONSTRAINT loan_requests_status_check
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SYSTEM_REJECTED', 'CANCELLED_AFTER_MEETING'));
    END IF;
END $$;
