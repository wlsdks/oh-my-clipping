-- 사용자 북마크 테이블
CREATE TABLE user_bookmarks (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(120) NOT NULL,
    summary_id VARCHAR(36) NOT NULL REFERENCES batch_summaries(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_user_bookmarks_user_summary ON user_bookmarks(user_id, summary_id);
CREATE INDEX idx_user_bookmarks_user_created ON user_bookmarks(user_id, created_at DESC);

-- 히스토리 조회 성능용 인덱스 (H2 호환)
CREATE INDEX idx_batch_summaries_sent_cat_created
    ON batch_summaries(is_sent_to_slack, category_id, created_at DESC);
