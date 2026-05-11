CREATE TABLE original_contents (
    id VARCHAR(36) PRIMARY KEY,
    rss_item_id VARCHAR(36) NOT NULL UNIQUE REFERENCES rss_items(id) ON DELETE CASCADE,
    source_link VARCHAR(2000) NOT NULL UNIQUE,
    title VARCHAR(1000) NOT NULL,
    markdown TEXT NOT NULL,
    content_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_original_contents_rss_item_id ON original_contents(rss_item_id);
CREATE INDEX idx_original_contents_source_link ON original_contents(source_link);
