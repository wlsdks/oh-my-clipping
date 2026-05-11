CREATE TABLE competitor_watchlist (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    keywords TEXT NOT NULL,
    tier VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_competitor_tier CHECK (tier IN ('DIRECT', 'ADJACENT', 'GLOBAL'))
);

CREATE INDEX idx_competitor_watchlist_active ON competitor_watchlist(is_active);
