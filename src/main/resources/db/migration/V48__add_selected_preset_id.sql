-- V48__add_selected_preset_id.sql
-- 유저 요청에 프리셋 참조 ID 추가

ALTER TABLE clipping_user_requests ADD COLUMN selected_preset_id VARCHAR(36)
    REFERENCES clipping_personas(id);
