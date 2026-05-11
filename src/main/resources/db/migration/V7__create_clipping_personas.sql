CREATE TABLE clipping_personas (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    system_prompt TEXT NOT NULL,
    summary_style TEXT,
    target_audience TEXT,
    max_items INT NOT NULL DEFAULT 5,
    language VARCHAR(10) NOT NULL DEFAULT 'ko',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
