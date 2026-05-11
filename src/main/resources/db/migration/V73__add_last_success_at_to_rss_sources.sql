-- Add last_success_at to rss_sources for health tracking
-- Set NULL initially; will be populated by collection success handler
ALTER TABLE rss_sources ADD COLUMN last_success_at TIMESTAMP NULL;

-- Index for unhealthy source queries (sorted by oldest success first)
CREATE INDEX idx_rss_sources_last_success_at ON rss_sources(last_success_at);
