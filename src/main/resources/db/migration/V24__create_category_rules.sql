CREATE TABLE clipping_category_rules (
    category_id VARCHAR(36) PRIMARY KEY REFERENCES batch_categories(id) ON DELETE CASCADE,
    include_keywords TEXT NOT NULL DEFAULT '[]',
    exclude_keywords TEXT NOT NULL DEFAULT '[]',
    risk_tags TEXT NOT NULL DEFAULT '[]',
    include_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.55,
    review_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.35,
    uncertain_to_review BOOLEAN NOT NULL DEFAULT TRUE,
    auto_exclude_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_category_rule_thresholds CHECK (
        include_threshold >= 0.0
        AND include_threshold <= 1.0
        AND review_threshold >= 0.0
        AND review_threshold <= 1.0
        AND include_threshold >= review_threshold
    )
);

CREATE INDEX idx_category_rules_updated_at
    ON clipping_category_rules (updated_at DESC);
