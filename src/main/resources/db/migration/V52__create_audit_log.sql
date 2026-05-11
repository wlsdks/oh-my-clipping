CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    VARCHAR(36) NOT NULL,
    actor_name  VARCHAR(100) NOT NULL,
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id   VARCHAR(100),
    target_name VARCHAR(200),
    detail      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_actor ON audit_log(actor_id);
CREATE INDEX idx_audit_log_target ON audit_log(target_type, target_id);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);
