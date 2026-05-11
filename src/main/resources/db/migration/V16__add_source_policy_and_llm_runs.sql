ALTER TABLE rss_sources
    ADD COLUMN legal_basis VARCHAR(40) NOT NULL DEFAULT 'QUOTATION_ONLY';

ALTER TABLE rss_sources
    ADD COLUMN summary_allowed BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE rss_sources
    ADD COLUMN fulltext_allowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE rss_sources
    ADD COLUMN terms_reviewed_at TIMESTAMP;

ALTER TABLE rss_sources
    ADD COLUMN review_notes TEXT;

ALTER TABLE rss_sources
    ADD CONSTRAINT chk_rss_sources_legal_basis
        CHECK (legal_basis IN ('LICENSED', 'OPEN_LICENSE', 'QUOTATION_ONLY', 'PROHIBITED'));

CREATE TABLE llm_runs (
    id VARCHAR(36) PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    rss_item_id VARCHAR(36) REFERENCES rss_items(id) ON DELETE SET NULL,
    model VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    input_chars INT NOT NULL,
    output_chars INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    duration_ms BIGINT NOT NULL,
    tokens_in INT,
    tokens_out INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE llm_runs
    ADD CONSTRAINT chk_llm_runs_status
        CHECK (status IN ('SUCCEEDED', 'EMPTY_RESULT', 'FAILED'));

CREATE INDEX idx_llm_runs_category_created ON llm_runs(category_id, created_at DESC);
CREATE INDEX idx_llm_runs_rss_item ON llm_runs(rss_item_id);
