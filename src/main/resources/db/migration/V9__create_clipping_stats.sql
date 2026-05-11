CREATE TABLE clipping_stats (
    id VARCHAR(36) PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    stat_date DATE NOT NULL,
    items_collected INT NOT NULL DEFAULT 0,
    items_summarized INT NOT NULL DEFAULT 0,
    items_sent INT NOT NULL DEFAULT 0,
    top_keywords TEXT,
    avg_importance_score FLOAT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(category_id, stat_date)
);

CREATE INDEX idx_clipping_stats_category_date ON clipping_stats(category_id, stat_date);
