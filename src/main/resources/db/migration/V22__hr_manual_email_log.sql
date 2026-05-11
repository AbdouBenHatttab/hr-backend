CREATE TABLE hr_manual_email_log (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    sent_by_user_id BIGINT NOT NULL,
    sent_by_username VARCHAR(150) NOT NULL,
    sent_by_display_name VARCHAR(255) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body_preview VARCHAR(1000) NOT NULL,
    reference_type VARCHAR(100),
    reference_id BIGINT,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL
);

CREATE INDEX idx_hr_manual_email_log_created_at ON hr_manual_email_log(created_at);
CREATE INDEX idx_hr_manual_email_log_recipient_user_id ON hr_manual_email_log(recipient_user_id);
CREATE INDEX idx_hr_manual_email_log_sent_by_user_id ON hr_manual_email_log(sent_by_user_id);
CREATE INDEX idx_hr_manual_email_log_status ON hr_manual_email_log(status);
