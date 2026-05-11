CREATE TABLE clipping_runtime_settings_audits (
    id VARCHAR(36) PRIMARY KEY,
    setting_key VARCHAR(120) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    action VARCHAR(20) NOT NULL,
    changed_by VARCHAR(120) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_runtime_settings_audits_changed_at
    ON clipping_runtime_settings_audits (changed_at DESC);
