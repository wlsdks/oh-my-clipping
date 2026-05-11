-- H2 호환 레이어: PostgreSQL 전용 함수를 H2 함수로 매핑한다.
-- Flyway beforeMigrate 콜백으로 모든 마이그레이션 전에 실행된다.
CREATE ALIAS IF NOT EXISTS "gen_random_uuid" FOR "java.util.UUID.randomUUID";
