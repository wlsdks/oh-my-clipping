ALTER TABLE rss_sources ADD COLUMN crawl_approved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rss_sources ADD COLUMN approved_by VARCHAR(100);
ALTER TABLE rss_sources ADD COLUMN approved_at TIMESTAMP;
ALTER TABLE rss_sources ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE rss_sources ADD COLUMN reliability_score INT NOT NULL DEFAULT 50;
ALTER TABLE rss_sources ADD COLUMN last_crawl_error TEXT;
ALTER TABLE rss_sources ADD COLUMN crawl_fail_count INT NOT NULL DEFAULT 0;
