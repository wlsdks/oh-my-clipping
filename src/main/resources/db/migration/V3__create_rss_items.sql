CREATE TABLE rss_items (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(1000) NOT NULL,
    content TEXT,
    link VARCHAR(2000) NOT NULL UNIQUE,
    published_at TIMESTAMP,
    language VARCHAR(20) NOT NULL DEFAULT 'FOREIGN',
    is_processed BOOLEAN NOT NULL DEFAULT FALSE,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    rss_source_id VARCHAR(36) NOT NULL REFERENCES rss_sources(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rss_items_category_id ON rss_items(category_id);
CREATE INDEX idx_rss_items_is_processed ON rss_items(is_processed);
CREATE INDEX idx_rss_items_link ON rss_items(link);
