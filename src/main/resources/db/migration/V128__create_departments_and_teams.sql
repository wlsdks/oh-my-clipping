-- V128__create_departments_and_teams.sql
-- 부서/팀 조직도 정식화 (Round 3 스펙 §1.1, §1.2).
--
-- 배경:
-- 기존 admin_users.department 는 자유 텍스트 컬럼이라 "영업팀" vs "영업 팀" 등
-- 표기 드리프트가 누적됐다. Admin UI 로 관리 가능한 FK 체계로 전환하기 위해
-- departments + teams 두 테이블을 신설한다.
--
-- 규칙:
-- - name_normalized 에 DepartmentNormalizer.normalize() 결과를 저장해
--   대소문자/공백 차이로 인한 중복 조직을 차단한다.
-- - teams 는 department_id FK 로 상위 부서에 종속되며 ON DELETE CASCADE.
--   실제 운영은 soft-delete(is_active=false) 로만 처리하지만, 안전망으로 걸어둔다.
-- - 두 테이블 모두 is_active, display_order 로 Admin UI 정렬/표시 제어.
CREATE TABLE departments (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    name_normalized VARCHAR(100) NOT NULL UNIQUE,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_departments_active_order ON departments(is_active, display_order);

CREATE TABLE teams (
    id VARCHAR(36) PRIMARY KEY,
    department_id VARCHAR(36) NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    name_normalized VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(department_id, name_normalized)
);

CREATE INDEX idx_teams_dept_active_order ON teams(department_id, is_active, display_order);
