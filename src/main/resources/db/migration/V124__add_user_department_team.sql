-- Phase 3 PR1: User 에 팀(team) 필드 추가.
-- department 컬럼은 V23 에서 이미 VARCHAR(100) 으로 존재한다.
-- 추가로 team VARCHAR(64) 를 두어 부서 하위 조직을 분석 축으로 쓸 수 있게 한다.
-- 값은 서버에서 trim + lowercase + 공백 단일화 로 정규화해 저장한다 (DepartmentNormalizer).
ALTER TABLE admin_users ADD COLUMN team VARCHAR(64);
