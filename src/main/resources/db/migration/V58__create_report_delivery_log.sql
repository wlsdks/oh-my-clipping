CREATE TABLE report_delivery_log (
    id               VARCHAR(36) PRIMARY KEY,
    report_type      VARCHAR(20) NOT NULL,
    period_key       VARCHAR(20) NOT NULL,
    channel_id       VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    snapshot_id      VARCHAR(36),
    slack_message_ts VARCHAR(50),
    error_message    TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_report_delivery_log_status
        CHECK (status IN ('RESERVED', 'SENT', 'FAILED')),
    UNIQUE (report_type, period_key, channel_id)
);

CREATE INDEX idx_report_delivery_log_created_at ON report_delivery_log(created_at);
CREATE INDEX idx_report_delivery_log_status ON report_delivery_log(status);
