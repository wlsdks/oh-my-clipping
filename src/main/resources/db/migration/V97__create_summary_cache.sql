-- V97__create_summary_cache.sql
-- AI 요약 결과를 캐싱하여 동일 기사+페르소나 조합의 중복 Gemini 호출을 방지한다.
-- 캐시 키: SHA-256(title + content_preview + persona_id)

CREATE TABLE summary_cache (
    cache_key         VARCHAR(64) PRIMARY KEY,
    summary           TEXT NOT NULL,
    keywords          TEXT,
    importance_score  FLOAT NOT NULL DEFAULT 0.5,
    sentiment         VARCHAR(20),
    event_type        VARCHAR(30),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_summary_cache_created ON summary_cache(created_at);
