CREATE TABLE user_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    event_data  TEXT,
    page_path   VARCHAR(255),
    session_id  VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_events_user_id   ON user_events(user_id);
CREATE INDEX idx_user_events_type_date ON user_events(event_type, created_at);
CREATE INDEX idx_user_events_session   ON user_events(session_id);
CREATE INDEX idx_user_events_created   ON user_events(created_at);
