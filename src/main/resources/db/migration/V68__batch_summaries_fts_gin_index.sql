-- batch_summaries 전문 검색(FTS) GIN 인덱스 자리표시자.
--
-- H2 테스트 환경에서는 GIN과 to_tsvector를 지원하지 않으므로 이 기본 migration은
-- 빈 파일로 유지한다. 운영 PostgreSQL 인덱스는 db/migration-pg/V147 에서 생성한다.

-- 이 마이그레이션 파일은 빈 파일로 두어 Flyway 버전 순서를 유지한다.
-- (H2 호환성을 위해 실제 GIN 인덱스 생성은 PostgreSQL 전용 migration 경로에서만 적용)
