CREATE TABLE clipping_retention_policies (
    id VARCHAR(36) PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL UNIQUE REFERENCES batch_categories(id) ON DELETE CASCADE,
    keep_days INT NOT NULL CHECK (keep_days > 0),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_retention_policies_category_id ON clipping_retention_policies(category_id);
