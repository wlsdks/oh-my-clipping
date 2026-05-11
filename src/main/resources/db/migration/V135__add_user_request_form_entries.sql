-- V135__add_user_request_form_entries.sql
-- Account-Based News Intelligence:
-- 위자드 entries 배열을 JSON 문자열로 보존 (keyword/company 구분 유지).
-- NULL 이면 legacy 단일 source 경로 — 하위 호환.
ALTER TABLE clipping_user_requests ADD COLUMN IF NOT EXISTS form_entries TEXT;
