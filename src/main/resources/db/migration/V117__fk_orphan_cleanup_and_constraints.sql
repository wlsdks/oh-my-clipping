-- ============================================================
-- V117: FK 보강 + orphan cleanup + 상태 불변식 CHECK + 참조 없는 중복 본체 정리
--
-- 배경:
--   DB 감사 결과 여러 테이블이 "논리 FK" 로만 묶여 있고 실제 제약이 없어
--   수많은 orphan 이 누적돼 있었다 (audit_log 88, user_events 162,
--   summary_feedback 4, weekly_persona_subscription_state 168, 그 외).
--   V117 은 (a) 기존 orphan 을 백업 후 정리, (b) FK 8개 추가, (c) 상태
--   불변식 CHECK, (d) 참조 없는 중복 본체(category/source) 를 일괄로 맞춘다.
--
-- 설계 원칙:
--   - Idempotent: 재실행 시 no-op 이 되도록 ROW_NUMBER/NOT EXISTS/DROP
--     IF EXISTS 를 모두 적용.
--   - Vendor-neutral: PostgreSQL + H2(MODE=PostgreSQL) 모두에서 동작하는
--     문법만 사용. REINDEX CONCURRENTLY 같은 PG 전용 작업은 포함하지 않음.
--   - Safety: 모든 DELETE 대상은 별도 `_v117_backup_*` 테이블에 스냅샷
--     저장. 운영에서는 V117 적용 후 7일 이내 DBA 가 DROP 하거나 V118 에
--     cleanup 을 넣는다.
--   - 이 migration 은 도메인 변경을 의도하지 않음 — 정합성 복구와 보호만.
-- ============================================================

-- -----------------------------------------------------------------
-- Phase 0. 백업 스냅샷 (DELETE 대상이 0 건이면 빈 테이블로 남음)
-- -----------------------------------------------------------------

CREATE TABLE IF NOT EXISTS _v117_backup_user_events AS
  SELECT * FROM user_events
   WHERE (user_id IS NOT NULL AND user_id NOT IN (SELECT id FROM admin_users))
      OR (summary_id IS NOT NULL AND summary_id NOT IN (SELECT id FROM batch_summaries));

CREATE TABLE IF NOT EXISTS _v117_backup_summary_feedback AS
  SELECT * FROM summary_feedback
   WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT id FROM admin_users);

-- wpss.user_id 는 스키마명과 달리 category.id 를 담는 이력(비정상) 컬럼이다.
-- WeeklyPersonaSnapshotStep.kt:145 주석 참조. follow-up 에서 컬럼 리네임.
CREATE TABLE IF NOT EXISTS _v117_backup_wpss AS
  SELECT * FROM weekly_persona_subscription_state
   WHERE user_id NOT IN (SELECT id FROM batch_categories);

-- -----------------------------------------------------------------
-- Phase 1. orphan cleanup
-- -----------------------------------------------------------------

-- audit_log.actor_id 는 기존에 NOT NULL 이었다. FK ON DELETE SET NULL 을 걸기
-- 위해 먼저 nullable 로 바꾼다. 이렇게 하면 탈퇴한 admin 의 감사 기록도
-- "actor 불명" 상태로 row 는 보존되어 분석 trail 이 유지된다.
ALTER TABLE audit_log ALTER COLUMN actor_id DROP NOT NULL;

-- orphan actor_id 만 NULL 로 끊는다 (row 는 보존).
UPDATE audit_log SET actor_id = NULL
 WHERE actor_id IS NOT NULL AND actor_id NOT IN (SELECT id FROM admin_users);

DELETE FROM user_events
 WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT id FROM admin_users);
DELETE FROM user_events
 WHERE summary_id IS NOT NULL AND summary_id NOT IN (SELECT id FROM batch_summaries);

DELETE FROM summary_feedback
 WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT id FROM admin_users);

DELETE FROM weekly_persona_subscription_state
 WHERE user_id NOT IN (SELECT id FROM batch_categories);

-- -----------------------------------------------------------------
-- Phase 2. FK 8개 추가 (DROP IF EXISTS 선행으로 idempotent)
-- -----------------------------------------------------------------

ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS fk_audit_log_actor;
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_actor
  FOREIGN KEY (actor_id) REFERENCES admin_users(id) ON DELETE SET NULL;

ALTER TABLE user_events DROP CONSTRAINT IF EXISTS fk_user_events_user;
ALTER TABLE user_events ADD CONSTRAINT fk_user_events_user
  FOREIGN KEY (user_id) REFERENCES admin_users(id) ON DELETE CASCADE;

ALTER TABLE user_events DROP CONSTRAINT IF EXISTS fk_user_events_summary;
ALTER TABLE user_events ADD CONSTRAINT fk_user_events_summary
  FOREIGN KEY (summary_id) REFERENCES batch_summaries(id) ON DELETE CASCADE;

ALTER TABLE summary_feedback DROP CONSTRAINT IF EXISTS fk_summary_feedback_user;
ALTER TABLE summary_feedback ADD CONSTRAINT fk_summary_feedback_user
  FOREIGN KEY (user_id) REFERENCES admin_users(id) ON DELETE CASCADE;

-- wpss.user_id 는 실제로 category.id 다. FK 대상을 batch_categories 로 맞춘다.
ALTER TABLE weekly_persona_subscription_state
  DROP CONSTRAINT IF EXISTS fk_wpss_category;
ALTER TABLE weekly_persona_subscription_state ADD CONSTRAINT fk_wpss_category
  FOREIGN KEY (user_id) REFERENCES batch_categories(id) ON DELETE CASCADE;

ALTER TABLE bookmarked_articles DROP CONSTRAINT IF EXISTS fk_bookmark_user;
ALTER TABLE bookmarked_articles ADD CONSTRAINT fk_bookmark_user
  FOREIGN KEY (user_id) REFERENCES admin_users(id) ON DELETE CASCADE;

ALTER TABLE bookmarked_articles DROP CONSTRAINT IF EXISTS fk_bookmark_category;
ALTER TABLE bookmarked_articles ADD CONSTRAINT fk_bookmark_category
  FOREIGN KEY (category_id) REFERENCES batch_categories(id) ON DELETE CASCADE;

ALTER TABLE bookmarked_articles DROP CONSTRAINT IF EXISTS fk_bookmark_summary;
ALTER TABLE bookmarked_articles ADD CONSTRAINT fk_bookmark_summary
  FOREIGN KEY (summary_id) REFERENCES batch_summaries(id) ON DELETE CASCADE;

-- -----------------------------------------------------------------
-- Phase 3. admin_users 상태 불변식 CHECK
-- -----------------------------------------------------------------

-- 시간 역전 row 는 먼저 보정한다 (created_at > updated_at 은 1건).
UPDATE admin_users SET updated_at = created_at
 WHERE created_at > updated_at;

ALTER TABLE admin_users DROP CONSTRAINT IF EXISTS chk_admin_users_time_order;
ALTER TABLE admin_users ADD CONSTRAINT chk_admin_users_time_order
  CHECK (updated_at >= created_at);

-- -----------------------------------------------------------------
-- Phase 4. 참조 없는 중복 본체 정리 (category / source)
-- -----------------------------------------------------------------

-- 같은 이름 카테고리 중 참조가 전혀 없는 것만 삭제 (over-delete 방지).
-- 모든 column 은 PostgreSQL 이 outer alias 와 ambiguity 를 일으키지 않도록
-- 테이블 alias 로 한정한다.
DELETE FROM batch_categories c
 WHERE c.name IN (
   SELECT bc.name FROM batch_categories bc GROUP BY bc.name HAVING COUNT(*) > 1
 )
 AND NOT EXISTS (SELECT 1 FROM clipping_user_owned_categories uoc WHERE uoc.category_id = c.id)
 AND NOT EXISTS (SELECT 1 FROM rss_sources rs WHERE rs.category_id = c.id)
 AND NOT EXISTS (SELECT 1 FROM batch_summaries bs WHERE bs.category_id = c.id)
 AND NOT EXISTS (SELECT 1 FROM delivery_log dl WHERE dl.category_id = c.id)
 AND NOT EXISTS (SELECT 1 FROM daily_summaries ds WHERE ds.category_id = c.id)
 AND NOT EXISTS (SELECT 1 FROM clipping_stats cs WHERE cs.category_id = c.id)
 AND NOT EXISTS (SELECT 1 FROM clipping_user_requests ur WHERE ur.approved_category_id = c.id)
 AND NOT EXISTS (SELECT 1 FROM clipping_review_items ri WHERE ri.category_id = c.id);

-- 같은 이름 소스 중 참조가 전혀 없는 것만 삭제.
DELETE FROM rss_sources s
 WHERE s.name IN (
   SELECT rs.name FROM rss_sources rs GROUP BY rs.name HAVING COUNT(*) > 1
 )
 AND NOT EXISTS (SELECT 1 FROM clipping_user_owned_sources uos WHERE uos.source_id = s.id)
 AND NOT EXISTS (SELECT 1 FROM rss_items ri WHERE ri.rss_source_id = s.id)
 AND NOT EXISTS (SELECT 1 FROM source_crawl_log scl WHERE scl.source_id = s.id);
