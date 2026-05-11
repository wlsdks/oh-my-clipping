-- V138__digest_diff_log.sql
-- Phase D-1: shadow mode 기간 동안 기존 (legacy) digest 와 새 account-based digest 의
-- 결과를 같은 날짜에 나란히 기록. 운영자가 /admin/digest-diff 페이지에서 직접 대조 검토한다.
--
-- UNIQUE (category_id, digest_date) — 같은 날 같은 카테고리로 중복 insert 방지.
-- UNIQUE 충돌은 DigestDiffLogStore.insertIfAbsent 에서 catch 하여 idempotent 하게 처리.

CREATE TABLE IF NOT EXISTS digest_diff_log (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL,
    digest_date DATE NOT NULL,
    legacy_summary TEXT,
    new_summary TEXT,
    new_mode VARCHAR(32),
    sections_count INT NOT NULL DEFAULT 0,
    articles_count INT NOT NULL DEFAULT 0,
    cross_match_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ddl_category FOREIGN KEY (category_id)
        REFERENCES batch_categories(id) ON DELETE CASCADE,
    CONSTRAINT uq_ddl_category_date UNIQUE (category_id, digest_date)
);

CREATE INDEX IF NOT EXISTS idx_ddl_category_date ON digest_diff_log(category_id, digest_date DESC);
