-- 기존 카테고리는 모두 공개 (현재 동작 보존)
ALTER TABLE batch_categories ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT true;

-- 이후 신규 생성 시 기본값을 false로 변경
ALTER TABLE batch_categories ALTER COLUMN is_public SET DEFAULT false;

-- browse 쿼리 최적화용 인덱스
CREATE INDEX idx_batch_categories_active_public
ON batch_categories (is_active, is_public);
