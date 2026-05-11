CREATE TABLE slack_channel_daily_send_counts (
    channel_id VARCHAR(100) NOT NULL,
    send_date DATE NOT NULL,
    message_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (channel_id, send_date)
);

CREATE INDEX idx_slack_channel_daily_send_counts_date_channel
    ON slack_channel_daily_send_counts (send_date, channel_id);
