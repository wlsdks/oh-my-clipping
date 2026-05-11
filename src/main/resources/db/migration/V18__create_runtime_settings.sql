CREATE TABLE clipping_runtime_settings (
    setting_key VARCHAR(120) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
