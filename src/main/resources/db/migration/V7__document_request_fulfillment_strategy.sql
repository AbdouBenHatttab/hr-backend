DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'document_requests'
    ) THEN
        UPDATE document_requests
        SET fulfillment_mode = 'GENERATED'
        WHERE document_type = 'LEAVE_BALANCE_STATEMENT';

        UPDATE document_requests
        SET fulfillment_mode = 'UPLOADED'
        WHERE document_type IN (
            'EMPLOYMENT_CERTIFICATE',
            'SALARY_CERTIFICATE',
            'WORK_REFERENCE_LETTER',
            'CONTRACT_COPY',
            'EXPERIENCE_CERTIFICATE',
            'CUSTOM_ADMINISTRATIVE_LETTER'
        );
    END IF;
END $$;
