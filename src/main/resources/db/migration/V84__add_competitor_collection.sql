-- 기사 ↔ 경쟁사 다대다 관계 (하나의 기사가 여러 경쟁사에 매칭 가능)
CREATE TABLE batch_summary_competitors (
    summary_id VARCHAR(36) NOT NULL,
    competitor_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (summary_id, competitor_id),
    CONSTRAINT fk_bsc_summary FOREIGN KEY (summary_id) REFERENCES batch_summaries(id) ON DELETE CASCADE,
    CONSTRAINT fk_bsc_competitor FOREIGN KEY (competitor_id) REFERENCES competitor_watchlist(id) ON DELETE CASCADE
);
CREATE INDEX idx_bsc_competitor ON batch_summary_competitors(competitor_id);

-- 수동 RSS 피드 URL (관리자가 직접 추가한 경쟁사 블로그/프레스룸 등)
CREATE TABLE competitor_rss_feeds (
    id VARCHAR(36) PRIMARY KEY,
    competitor_id VARCHAR(36) NOT NULL,
    feed_url TEXT NOT NULL,
    label VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_crf_competitor FOREIGN KEY (competitor_id) REFERENCES competitor_watchlist(id) ON DELETE CASCADE,
    CONSTRAINT uq_crf_competitor_url UNIQUE (competitor_id, feed_url)
);
