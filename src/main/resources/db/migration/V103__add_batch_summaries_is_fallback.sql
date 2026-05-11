-- V103__add_batch_summaries_is_fallback.sql
-- fallback 요약(AI 실패 시 원문 발췌) 구분용 플래그.
-- 기본값 false로 기존 데이터에 영향 없음.
ALTER TABLE batch_summaries ADD COLUMN is_fallback BOOLEAN NOT NULL DEFAULT FALSE;

-- H2 호환을 위해 일반 composite index 사용 (partial index 금지).
CREATE INDEX idx_batch_summaries_fallback_created
    ON batch_summaries(is_fallback, created_at);
