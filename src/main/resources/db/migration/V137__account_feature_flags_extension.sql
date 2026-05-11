-- V137__account_feature_flags_extension.sql
-- Phase D-1: category_feature_flags 에 dual-section legend 표시 횟수 + shadow mode 컬럼 추가.
--
-- dual_legend_shown_at / dual_legend_display_count: DUAL_SECTION 모드에서 상단 legend 를
--   최초 3회까지만 full 형태로 노출하기 위한 카운터 (D7 에서 활용).
-- shadow_mode_enabled / shadow_enabled_at: canary 첫 1주 동안 Slack 실제 발송 없이
--   diff 만 digest_diff_log 로 기록하기 위한 토글 (D2/D6 에서 활용).

-- 각 ALTER 를 분리 — H2 MODE=PostgreSQL 은 multi-ADD 지원하지 않음.
ALTER TABLE category_feature_flags ADD COLUMN IF NOT EXISTS dual_legend_shown_at TIMESTAMP;
ALTER TABLE category_feature_flags ADD COLUMN IF NOT EXISTS dual_legend_display_count INT NOT NULL DEFAULT 0;
ALTER TABLE category_feature_flags ADD COLUMN IF NOT EXISTS shadow_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE category_feature_flags ADD COLUMN IF NOT EXISTS shadow_enabled_at TIMESTAMP;
