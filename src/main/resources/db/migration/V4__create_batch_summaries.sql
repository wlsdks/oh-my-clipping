CREATE TABLE batch_summaries (
    id VARCHAR(36) PRIMARY KEY,
    original_title VARCHAR(1000) NOT NULL,
    translated_title VARCHAR(1000),
    summary TEXT NOT NULL,
    keywords TEXT,
    insights TEXT,
    source_link VARCHAR(2000) NOT NULL,
    is_sent_to_slack BOOLEAN NOT NULL DEFAULT FALSE,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    rss_item_id VARCHAR(36) NOT NULL REFERENCES rss_items(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_batch_summaries_category_id ON batch_summaries(category_id);
CREATE INDEX idx_batch_summaries_is_sent ON batch_summaries(is_sent_to_slack);
