-- V129__add_admin_users_department_team_fk.sql
-- admin_users 에 department_id / team_id FK 컬럼 추가 (Round 3 스펙 §1.3).
--
-- 안전 장치:
-- 1. 두 컬럼 모두 nullable — 기존 레코드 backfill 없이 즉시 배포 가능.
-- 2. ON DELETE SET NULL — 부서/팀이 삭제되더라도 사용자 레코드는 보존.
-- 3. DROP CONSTRAINT IF EXISTS + ADD CONSTRAINT 조합으로 재실행 안전
--    (V117/V120 스타일 준수).
-- 4. 레거시 admin_users.department / team 자유 텍스트 컬럼은 6개월 유지 후 DROP.
--    당분간은 이름 캐시 용도로 서비스 레이어가 동기화한다.
ALTER TABLE admin_users ADD COLUMN department_id VARCHAR(36);
ALTER TABLE admin_users ADD COLUMN team_id VARCHAR(36);

ALTER TABLE admin_users DROP CONSTRAINT IF EXISTS fk_admin_users_department;
ALTER TABLE admin_users ADD CONSTRAINT fk_admin_users_department
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL;

ALTER TABLE admin_users DROP CONSTRAINT IF EXISTS fk_admin_users_team;
ALTER TABLE admin_users ADD CONSTRAINT fk_admin_users_team
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_admin_users_department ON admin_users(department_id);
CREATE INDEX IF NOT EXISTS idx_admin_users_team ON admin_users(team_id);
