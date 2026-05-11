-- 북마크된 기사 스냅샷 테이블.
-- 기존 user_bookmarks는 batch_summaries를 FK CASCADE로 참조해
-- retention purge 시 북마크도 같이 삭제되는 문제가 있었다.
-- 북마크 시점의 요약 내용을 복사 저장해 원본 라이프사이클과 분리한다.
CREATE TABLE bookmarked_articles (
    id                  VARCHAR(36) PRIMARY KEY,
    user_id             VARCHAR(120) NOT NULL,
    -- 원본 요약 ID는 참조만 하며 FK 제약은 걸지 않는다 (원본 삭제되어도 스냅샷 유지).
    summary_id          VARCHAR(36) NOT NULL,
    original_title      VARCHAR(1000) NOT NULL,
    translated_title    VARCHAR(1000),
    summary             TEXT NOT NULL,
    insights            TEXT,
    keywords            TEXT,
    importance_score    FLOAT NOT NULL DEFAULT 0,
    source_link         VARCHAR(2000) NOT NULL,
    category_id         VARCHAR(36) NOT NULL,
    sentiment           VARCHAR(20),
    event_type          VARCHAR(30),
    article_created_at  TIMESTAMP NOT NULL,
    bookmarked_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_bookmarked_articles_user_summary
    ON bookmarked_articles(user_id, summary_id);
CREATE INDEX idx_bookmarked_articles_user_bookmarked
    ON bookmarked_articles(user_id, bookmarked_at DESC);
CREATE INDEX idx_bookmarked_articles_category
    ON bookmarked_articles(category_id);

-- 기존 user_bookmarks 데이터를 스냅샷으로 이관한다.
-- 원본 요약이 아직 존재하는 행만 대상이며, 유실된 행은 이관하지 않는다.
INSERT INTO bookmarked_articles (
    id, user_id, summary_id, original_title, translated_title,
    summary, insights, keywords, importance_score, source_link,
    category_id, sentiment, event_type, article_created_at, bookmarked_at
)
SELECT
    ub.id, ub.user_id, ub.summary_id, bs.original_title, bs.translated_title,
    bs.summary, bs.insights, bs.keywords, bs.importance_score, bs.source_link,
    bs.category_id, bs.sentiment, bs.event_type, bs.created_at, ub.created_at
FROM user_bookmarks ub
JOIN batch_summaries bs ON bs.id = ub.summary_id;

-- 기존 북마크 테이블은 제거한다. user_bookmarks를 참조하던 인덱스도 함께 사라진다.
DROP TABLE user_bookmarks;
