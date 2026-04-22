DO $$
DECLARE
    status_constraint RECORD;
BEGIN
    IF to_regclass('public.leave_requests') IS NOT NULL THEN
        ALTER TABLE public.leave_requests
            ADD COLUMN IF NOT EXISTS canceled_by varchar(255),
            ADD COLUMN IF NOT EXISTS canceled_at timestamp;

        FOR status_constraint IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
            WHERE n.nspname = 'public'
              AND t.relname = 'leave_requests'
              AND a.attname = 'status'
              AND c.contype = 'c'
        LOOP
            EXECUTE format('ALTER TABLE public.leave_requests DROP CONSTRAINT IF EXISTS %I', status_constraint.conname);
        END LOOP;

        ALTER TABLE public.leave_requests
            ADD CONSTRAINT leave_requests_status_check
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED_BY_EMPLOYEE'));
    END IF;

    IF to_regclass('public.document_requests') IS NOT NULL THEN
        ALTER TABLE public.document_requests
            ADD COLUMN IF NOT EXISTS canceled_by varchar(255),
            ADD COLUMN IF NOT EXISTS canceled_at timestamp;

        FOR status_constraint IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
            WHERE n.nspname = 'public'
              AND t.relname = 'document_requests'
              AND a.attname = 'status'
              AND c.contype = 'c'
        LOOP
            EXECUTE format('ALTER TABLE public.document_requests DROP CONSTRAINT IF EXISTS %I', status_constraint.conname);
        END LOOP;

        ALTER TABLE public.document_requests
            ADD CONSTRAINT document_requests_status_check
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SYSTEM_REJECTED', 'CANCELLED_BY_EMPLOYEE', 'CANCELLED_AFTER_MEETING'));
    END IF;

    IF to_regclass('public.authorization_requests') IS NOT NULL THEN
        ALTER TABLE public.authorization_requests
            ADD COLUMN IF NOT EXISTS canceled_by varchar(255),
            ADD COLUMN IF NOT EXISTS canceled_at timestamp;

        FOR status_constraint IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
            WHERE n.nspname = 'public'
              AND t.relname = 'authorization_requests'
              AND a.attname = 'status'
              AND c.contype = 'c'
        LOOP
            EXECUTE format('ALTER TABLE public.authorization_requests DROP CONSTRAINT IF EXISTS %I', status_constraint.conname);
        END LOOP;

        ALTER TABLE public.authorization_requests
            ADD CONSTRAINT authorization_requests_status_check
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SYSTEM_REJECTED', 'CANCELLED_BY_EMPLOYEE', 'CANCELLED_AFTER_MEETING'));
    END IF;

    IF to_regclass('public.loan_requests') IS NOT NULL THEN
        ALTER TABLE public.loan_requests
            ADD COLUMN IF NOT EXISTS canceled_by varchar(255),
            ADD COLUMN IF NOT EXISTS canceled_at timestamp;

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
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SYSTEM_REJECTED', 'CANCELLED_BY_EMPLOYEE', 'CANCELLED_AFTER_MEETING'));
    END IF;
END $$;
