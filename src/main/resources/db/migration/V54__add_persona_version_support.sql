-- persona_versions 테이블 생성
CREATE TABLE persona_versions (
    id VARCHAR(36) PRIMARY KEY,
    persona_id VARCHAR(36) NOT NULL REFERENCES clipping_personas(id) ON DELETE CASCADE,
    version INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    system_prompt TEXT NOT NULL,
    summary_style TEXT,
    target_audience TEXT,
    max_items INT NOT NULL DEFAULT 5,
    language VARCHAR(10) NOT NULL DEFAULT 'ko',
    preview_title TEXT,
    preview_source TEXT,
    preview_body TEXT,
    change_summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(persona_id, version)
);

CREATE INDEX idx_persona_versions_persona_id ON persona_versions(persona_id);

-- clipping_personas에 신규 컬럼 추가
ALTER TABLE clipping_personas ADD COLUMN current_version INT NOT NULL DEFAULT 1;
ALTER TABLE clipping_personas ADD COLUMN tone VARCHAR(50);
ALTER TABLE clipping_personas ADD COLUMN length_pref VARCHAR(50);
ALTER TABLE clipping_personas ADD COLUMN addons TEXT;

-- 기존 프리셋의 초기 버전 스냅샷 삽입
INSERT INTO persona_versions (id, persona_id, version, name, description, system_prompt, summary_style, target_audience, max_items, language, preview_title, preview_source, preview_body, change_summary)
SELECT
    gen_random_uuid()::VARCHAR AS id,
    id AS persona_id,
    1 AS version,
    name,
    description,
    system_prompt,
    summary_style,
    target_audience,
    max_items,
    language,
    preview_title,
    preview_source,
    preview_body,
    '최초 버전' AS change_summary
FROM clipping_personas
WHERE is_preset = TRUE;
