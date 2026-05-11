-- importance_score 기반 정렬 쿼리를 위한 복합 인덱스.
-- MCP listTopSummaries 도구가 category_id + importance_score DESC + created_at DESC로 조회한다.
CREATE INDEX IF NOT EXISTS idx_batch_summaries_category_importance_created
    ON batch_summaries (category_id, importance_score DESC, created_at DESC);
