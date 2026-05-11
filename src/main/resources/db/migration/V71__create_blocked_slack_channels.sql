CREATE TABLE blocked_slack_channels (
    id              VARCHAR(36)  PRIMARY KEY,
    channel_id      VARCHAR(20)  NOT NULL,
    channel_name    VARCHAR(200) NOT NULL,
    blocked_by_user_id VARCHAR(36) NOT NULL,
    blocked_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_blocked_slack_channels_channel_id UNIQUE (channel_id)
);
