CREATE TABLE keyword_entities (
    id VARCHAR(36) PRIMARY KEY,
    keyword VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL,
    first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_keyword_entity UNIQUE (keyword)
);
CREATE INDEX idx_keyword_entity_category ON keyword_entities(category);
