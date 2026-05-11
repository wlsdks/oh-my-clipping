-- 경쟁사 타임라인 조회 성능 개선: competitor_id + summary_id 복합 인덱스
-- batch_summaries.created_at와 JOIN하므로 summary_id 기반 조회를 가속한다.
CREATE INDEX IF NOT EXISTS idx_bsc_competitor_summary
    ON batch_summary_competitors(competitor_id, summary_id);
