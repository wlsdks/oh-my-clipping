-- V133__sync_competitors_to_organizations.sql
-- 기존 competitor_watchlist 항목을 organizations 테이블로 백필한다.
-- 사용자가 "경쟁사 관리(/admin/competitors)"에서 등록한 데이터가
-- "조직 관리(/admin/organizations)"에도 자동 노출되도록 단일 관리점을 만든다.
--
-- H2 MODE=PostgreSQL 와 프로덕션 PostgreSQL 모두 호환:
--   - gen_random_uuid()::VARCHAR — V40/V54 선례에서 이미 사용 중 (양쪽 DB 지원)
--   - INSERT ... SELECT ... WHERE NOT EXISTS — V87 선례 (멱등성 보장)
--
-- 이 migration 은 멱등성을 가진다:
--   - 이미 같은 이름의 Organization 이 있으면 skip.
--   - 이후 서비스 계층의 sync hook(CompetitorOrganizationSynchronizer)이
--     신규 등록/수정/삭제를 실시간으로 mirror 한다.
INSERT INTO organizations (id, tenant_id, name, type, domain, description, created_at, updated_at)
SELECT
    gen_random_uuid()::VARCHAR AS id,
    'default' AS tenant_id,
    cw.name AS name,
    'COMPETITOR' AS type,
    NULL AS domain,
    '경쟁사 자동 동기 (V133 backfill)' AS description,
    cw.created_at,
    cw.updated_at
FROM competitor_watchlist cw
WHERE NOT EXISTS (
    SELECT 1
    FROM organizations o
    WHERE o.tenant_id = 'default'
      AND o.name = cw.name
);
