ALTER TABLE rss_sources
    ADD COLUMN source_region VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE rss_sources
    ADD CONSTRAINT chk_rss_sources_source_region
    CHECK (source_region IN ('GLOBAL', 'DOMESTIC', 'UNKNOWN'));

UPDATE rss_sources
SET source_region = CASE
    WHEN LOWER(url) LIKE '%.kr/%' OR LOWER(url) LIKE '%.kr' THEN 'DOMESTIC'
    ELSE 'GLOBAL'
END
WHERE source_region = 'UNKNOWN';
