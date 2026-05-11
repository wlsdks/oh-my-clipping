-- 주요 뉴스사이트 매핑 테이블: 소스 추가 시 사이트명/도메인으로 자동 탐색에 활용
CREATE TABLE IF NOT EXISTS known_news_sources (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    aliases     TEXT         NOT NULL DEFAULT '[]',
    domain      VARCHAR(200) NOT NULL,
    rss_url     VARCHAR(500) NOT NULL,
    region      VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_known_news_sources_domain ON known_news_sources(domain);

-- rss_sources 테이블에 curated(관리자 검증) 플래그 추가
ALTER TABLE rss_sources ADD COLUMN IF NOT EXISTS curated BOOLEAN NOT NULL DEFAULT FALSE;
