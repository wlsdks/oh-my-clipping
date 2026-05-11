-- ============================================================
-- V118: weekly_persona_subscription_state.user_id → category_id 리네임
--
-- 배경:
--   WeeklyPersonaSnapshotStep 가 구독 단위 집계를 wpss 에 저장하는데,
--   이 "구독"은 실제로 category 하나를 의미한다 (persona + category 조합).
--   컬럼명이 `user_id` 라 의미가 왜곡됐고, V117 에서도 FK 대상을 억지로
--   batch_categories 로 연결해야 했다. V118 은 이름을 정합성 있게 바꾼다.
--
--   PK / 2차 인덱스 / FK 의 컬럼 참조는 PostgreSQL 이 RENAME COLUMN 시
--   자동으로 따라 업데이트한다. 인덱스 이름에도 "user" 가 남지 않도록
--   같은 파일에서 리네임한다.
-- ============================================================

ALTER TABLE weekly_persona_subscription_state
  RENAME COLUMN user_id TO category_id;

ALTER INDEX IF EXISTS idx_wpss_user_week RENAME TO idx_wpss_category_week;
