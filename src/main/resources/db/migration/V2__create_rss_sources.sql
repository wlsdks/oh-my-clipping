CREATE TABLE rss_sources (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    emoji VARCHAR(10),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rss_sources_category_id ON rss_sources(category_id);
