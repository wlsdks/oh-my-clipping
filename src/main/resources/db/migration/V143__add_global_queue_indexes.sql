CREATE INDEX IF NOT EXISTS idx_rss_items_processed_created
    ON rss_items(is_processed, created_at);

CREATE INDEX IF NOT EXISTS idx_batch_summaries_sent_created
    ON batch_summaries(is_sent_to_slack, created_at);
