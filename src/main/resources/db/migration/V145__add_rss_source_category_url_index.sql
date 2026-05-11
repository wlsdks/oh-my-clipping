CREATE INDEX IF NOT EXISTS idx_rss_sources_category_url
    ON rss_sources(category_id, url);
