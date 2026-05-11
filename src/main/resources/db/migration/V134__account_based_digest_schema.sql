-- V134__account_based_digest_schema.sql
-- Account-Based News Intelligence (Phase A):
-- organizations 에 stock_code / aliases / origin 컬럼 추가,
-- rss_sources 에 origin 컬럼 추가,
-- category_feature_flags 테이블 신규 (per-category 기능 토글),
-- category_section_silence_log 테이블 신규 (3일 연속 empty 추적).

-- 1. organizations 확장 (각 ALTER 분리 — H2 multi-ADD 미지원)
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS stock_code VARCHAR(20);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS aliases TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS origin VARCHAR(32);

ALTER TABLE organizations ADD CONSTRAINT chk_organizations_origin
  CHECK (origin IS NULL OR origin IN ('user_wizard','admin_created','competitor_mirror','backfill','legacy'));

CREATE INDEX IF NOT EXISTS idx_organizations_stock_code ON organizations(stock_code);
CREATE INDEX IF NOT EXISTS idx_organizations_origin ON organizations(origin);

-- 2. rss_sources 확장
ALTER TABLE rss_sources ADD COLUMN IF NOT EXISTS origin VARCHAR(32) DEFAULT 'manual';
ALTER TABLE rss_sources ADD CONSTRAINT chk_rss_sources_origin
  CHECK (origin IN ('manual','auto_generated','legacy'));
CREATE INDEX IF NOT EXISTS idx_rss_sources_origin ON rss_sources(origin);

-- 3. category_feature_flags
CREATE TABLE IF NOT EXISTS category_feature_flags (
    category_id VARCHAR(36) NOT NULL PRIMARY KEY,
    account_based_digest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cff_category FOREIGN KEY (category_id)
        REFERENCES batch_categories(id) ON DELETE CASCADE
);

-- 4. category_section_silence_log
CREATE TABLE IF NOT EXISTS category_section_silence_log (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL,
    section_key VARCHAR(32) NOT NULL,
    consecutive_empty_days INT NOT NULL DEFAULT 0,
    last_empty_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cssl_category FOREIGN KEY (category_id)
        REFERENCES batch_categories(id) ON DELETE CASCADE,
    CONSTRAINT uq_cssl UNIQUE (category_id, section_key)
);
