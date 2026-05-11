CREATE TABLE clipping_category_region_policies (
    category_id VARCHAR(36) PRIMARY KEY REFERENCES batch_categories(id) ON DELETE CASCADE,
    global_keywords TEXT NOT NULL DEFAULT '[]',
    domestic_keywords TEXT NOT NULL DEFAULT '[]',
    ambiguity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    uncertain_to_review BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_category_region_policy_threshold CHECK (
        ambiguity_threshold >= 0.0
        AND ambiguity_threshold <= 1.0
    )
);

CREATE INDEX idx_category_region_policies_updated_at
    ON clipping_category_region_policies (updated_at DESC);
