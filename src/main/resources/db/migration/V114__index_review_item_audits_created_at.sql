-- V114: review_item_audits 3년 retention 쿼리를 위한 created_at 단독 인덱스.
--
-- 배경: DataCleanupScheduler가 매일 03:15에 `WHERE created_at < ?` 조건으로
--       오래된 감사 이력을 삭제한다. 기존 idx_review_item_audits_summary_created_at
--       (summary_id, created_at DESC)는 summary_id가 선행 컬럼이라 retention
--       범위 스캔에 쓰이지 않는다. 단독 인덱스를 두어 3년 경계 삭제 쿼리가
--       index range scan을 쓸 수 있게 한다.
CREATE INDEX IF NOT EXISTS idx_review_item_audits_created_at
    ON clipping_review_item_audits (created_at);
