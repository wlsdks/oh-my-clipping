-- 전체 카테고리 중요도 상위 기사 조회 최적화.
-- TopArticlesService가 기간 조건 + importance_score DESC 정렬 + LIMIT으로 조회한다.
CREATE INDEX IF NOT EXISTS idx_batch_summaries_importance_created
    ON batch_summaries(importance_score DESC, created_at DESC);

-- 짧은 날짜 범위의 전체 카테고리 조회에서는 created_at 조건 선택도가 더 높을 수 있어
-- date-window path 를 보조한다. 기존 importance-first 인덱스는 긴 기간 top-N path 를 유지한다.
CREATE INDEX IF NOT EXISTS idx_batch_summaries_created_importance
    ON batch_summaries(created_at DESC, importance_score DESC);
