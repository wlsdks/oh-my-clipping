CREATE TABLE daily_summaries (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    total_items INT NOT NULL DEFAULT 0,
    summary_date DATE NOT NULL,
    content_guides TEXT,
    overall_summary TEXT NOT NULL,
    glossary TEXT,
    is_sent_to_slack BOOLEAN NOT NULL DEFAULT FALSE,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_daily_summaries_category_date ON daily_summaries(category_id, summary_date);
