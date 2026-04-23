DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'authorization_requests'
    ) THEN
        UPDATE authorization_requests
        SET authorization_type = 'TIME_PERMISSION'
        WHERE authorization_type IN (
            'WORK_FROM_HOME',
            'OVERTIME',
            'EARLY_DEPARTURE',
            'LATE_ARRIVAL'
        );
    END IF;
END $$;
