CREATE TABLE clipping_review_item_audits (
    id VARCHAR(36) PRIMARY KEY,
    summary_id VARCHAR(36) NOT NULL REFERENCES batch_summaries(id) ON DELETE CASCADE,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    reason TEXT,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_item_audits_from_status
        CHECK (from_status IS NULL OR from_status IN ('INCLUDE', 'REVIEW', 'EXCLUDE')),
    CONSTRAINT chk_review_item_audits_to_status
        CHECK (to_status IN ('INCLUDE', 'REVIEW', 'EXCLUDE'))
);

CREATE INDEX idx_review_item_audits_summary_created_at
    ON clipping_review_item_audits (summary_id, created_at DESC);
