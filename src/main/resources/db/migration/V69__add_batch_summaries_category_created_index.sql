-- 카테고리별 요약 조회 성능 개선 (다이제스트/트렌드 분석에서 빈번 사용)
CREATE INDEX IF NOT EXISTS idx_batch_summaries_category_created
    ON batch_summaries (category_id, created_at DESC);

-- 일일 요약 카테고리별 조회 성능 개선
CREATE INDEX IF NOT EXISTS idx_daily_summaries_category_date
    ON daily_summaries (category_id, summary_date DESC);
