ALTER TABLE admin_users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

ALTER TABLE admin_users
    ADD CONSTRAINT chk_admin_users_role
        CHECK (role IN ('ADMIN', 'USER'));

CREATE INDEX idx_admin_users_role ON admin_users(role);

CREATE TABLE clipping_user_requests (
    id VARCHAR(36) PRIMARY KEY,
    requester_user_id VARCHAR(36) NOT NULL REFERENCES admin_users(id),
    request_name VARCHAR(120) NOT NULL,
    source_name VARCHAR(120) NOT NULL,
    source_url VARCHAR(2000) NOT NULL,
    slack_channel_id VARCHAR(80) NOT NULL,
    persona_name VARCHAR(120) NOT NULL,
    persona_prompt TEXT NOT NULL,
    summary_style VARCHAR(120),
    target_audience VARCHAR(120),
    request_note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_note TEXT,
    reviewed_by_user_id VARCHAR(36) REFERENCES admin_users(id),
    reviewed_at TIMESTAMP,
    approved_category_id VARCHAR(36) REFERENCES batch_categories(id),
    approved_persona_id VARCHAR(36) REFERENCES clipping_personas(id),
    approved_source_id VARCHAR(36) REFERENCES rss_sources(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE clipping_user_requests
    ADD CONSTRAINT chk_clipping_user_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

CREATE INDEX idx_clipping_user_requests_requester ON clipping_user_requests(requester_user_id, created_at DESC);
CREATE INDEX idx_clipping_user_requests_status ON clipping_user_requests(status, created_at DESC);
