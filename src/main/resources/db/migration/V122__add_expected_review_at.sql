-- ============================================================
-- V120: 저작권 재검토 예정일 컬럼 추가 (expected_review_at)
--
-- 배경:
--   저작권 검토(`terms_reviewed_at`)는 6개월(180일) 주기로 재검토가
--   필요하다. 만료 임박/만료 소스를 UI 필터와 사이드바 뱃지로 드러내려면
--   `terms_reviewed_at + 180 days` 기준을 WHERE 절에서 쉽게 비교할 수
--   있어야 한다.
--
-- 설계 결정:
--   - 계산 컬럼(generated column)은 H2/PostgreSQL 간 호환이 애매해서
--     단순 nullable 컬럼으로 두고, 서비스 레이어(AdminSourceService)가
--     terms_reviewed_at 변경 시 함께 업데이트한다.
--   - 기존 데이터 backfill 은 DB 방언 차이(H2 `DATEADD` vs PostgreSQL
--     `INTERVAL '180 days'`)를 피하기 위해 별도 Java 마이그레이션
--     (`V121__backfill_expected_review_at`)에서 JDBC 로 처리한다.
--   - 인덱스는 필터 성능을 위해 `expected_review_at` 단일 컬럼에만 건다.
-- ============================================================

ALTER TABLE rss_sources
    ADD COLUMN IF NOT EXISTS expected_review_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_rss_sources_expected_review_at
    ON rss_sources(expected_review_at);
