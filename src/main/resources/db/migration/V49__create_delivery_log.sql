CREATE TABLE delivery_log (
    id            VARCHAR(36) PRIMARY KEY,
    category_id   VARCHAR(100) NOT NULL,
    channel_id    VARCHAR(100) NOT NULL,
    delivery_date DATE NOT NULL,
    delivery_hour INT NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    item_count    INT NOT NULL DEFAULT 0,
    slack_message_ts VARCHAR(50),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (category_id, channel_id, delivery_date, delivery_hour)
);

CREATE INDEX idx_delivery_log_date ON delivery_log(delivery_date);
CREATE INDEX idx_delivery_log_status ON delivery_log(status);
