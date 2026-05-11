CREATE TABLE source_crawl_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id VARCHAR(36) NOT NULL,
    crawled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    response_time_ms INT,
    articles_found INT DEFAULT 0,
    CONSTRAINT fk_crawl_log_source FOREIGN KEY (source_id) REFERENCES rss_sources(id) ON DELETE CASCADE
);

CREATE INDEX idx_crawl_log_source_time ON source_crawl_log(source_id, crawled_at DESC);
CREATE INDEX idx_crawl_log_crawled_at ON source_crawl_log(crawled_at);
