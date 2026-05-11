CREATE TABLE clipping_user_owned_personas (
    user_id VARCHAR(36) NOT NULL REFERENCES admin_users(id),
    persona_id VARCHAR(36) NOT NULL REFERENCES clipping_personas(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, persona_id)
);

CREATE TABLE clipping_user_owned_categories (
    user_id VARCHAR(36) NOT NULL REFERENCES admin_users(id),
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, category_id)
);

CREATE TABLE clipping_user_owned_sources (
    user_id VARCHAR(36) NOT NULL REFERENCES admin_users(id),
    source_id VARCHAR(36) NOT NULL REFERENCES rss_sources(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, source_id)
);

CREATE INDEX idx_clipping_user_owned_personas_user ON clipping_user_owned_personas(user_id, created_at DESC);
CREATE INDEX idx_clipping_user_owned_categories_user ON clipping_user_owned_categories(user_id, created_at DESC);
CREATE INDEX idx_clipping_user_owned_sources_user ON clipping_user_owned_sources(user_id, created_at DESC);
