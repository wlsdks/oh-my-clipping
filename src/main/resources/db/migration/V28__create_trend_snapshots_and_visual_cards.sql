CREATE TABLE clipping_trend_snapshots (
    id VARCHAR(36) PRIMARY KEY,
    period_type VARCHAR(20) NOT NULL,
    snapshot_from DATE NOT NULL,
    snapshot_to DATE NOT NULL,
    category_id VARCHAR(36) REFERENCES batch_categories(id) ON DELETE SET NULL,
    category_name VARCHAR(120) NOT NULL,
    region_type VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    summary TEXT NOT NULL,
    key_signals TEXT NOT NULL,
    action_items TEXT NOT NULL,
    source_count INTEGER NOT NULL DEFAULT 0,
    item_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    generated_by VARCHAR(100),
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trend_snapshots_period_type CHECK (period_type IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_trend_snapshots_region_type CHECK (region_type IN ('ALL', 'GLOBAL', 'DOMESTIC')),
    CONSTRAINT chk_trend_snapshots_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_trend_snapshots_period_created
    ON clipping_trend_snapshots (period_type, created_at DESC);

CREATE TABLE clipping_trend_visual_cards (
    id VARCHAR(36) PRIMARY KEY,
    snapshot_id VARCHAR(36) NOT NULL REFERENCES clipping_trend_snapshots(id) ON DELETE CASCADE,
    card_type VARCHAR(20) NOT NULL,
    title VARCHAR(300) NOT NULL,
    summary TEXT NOT NULL,
    panels TEXT NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_note TEXT,
    generated_by VARCHAR(100),
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trend_visual_cards_type CHECK (card_type IN ('INFO_CARD', 'COMIC_4', 'COMIC_8')),
    CONSTRAINT chk_trend_visual_cards_review_status CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_trend_visual_cards_snapshot_created
    ON clipping_trend_visual_cards (snapshot_id, created_at DESC);
