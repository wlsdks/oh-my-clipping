CREATE TABLE clipping_review_items (
    summary_id VARCHAR(36) PRIMARY KEY REFERENCES batch_summaries(id) ON DELETE CASCADE,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    reason TEXT,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_items_status CHECK (status IN ('INCLUDE', 'REVIEW', 'EXCLUDE'))
);

CREATE INDEX idx_review_items_category_status_updated_at
    ON clipping_review_items (category_id, status, updated_at DESC);
