-- V113__add_delivery_log_fallback_used.sql
-- Rename chain: V108 → V112 (#364, collided with #353 V108) → V113 (#365, collided with #363 V112).
-- Next free slot was V113 at the time this was written.
-- Slack payload 에러로 Block Kit 발송에 실패해 text-only fallback 으로 대체된 건을 표시한다.
-- 기본값 false 로 기존 데이터 영향 없음. H2/PostgreSQL 모두 동일 구문으로 동작한다.
ALTER TABLE delivery_log ADD COLUMN IF NOT EXISTS fallback_used BOOLEAN NOT NULL DEFAULT FALSE;

-- 24 시간 내 fallback 발송이 반복되는 카테고리/채널 탐지에 사용할 보조 인덱스.
-- H2 호환을 위해 partial index 대신 일반 composite index 를 사용한다.
CREATE INDEX IF NOT EXISTS idx_delivery_log_fallback_created
    ON delivery_log(fallback_used, created_at);
