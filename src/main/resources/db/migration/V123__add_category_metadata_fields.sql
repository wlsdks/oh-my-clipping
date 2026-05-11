-- Phase 3 PR1: Category 에 분석 목적 metadata 필드 추가.
-- purpose: 구독 목적 (영업/리서치 등). CHECK enum 으로 제약.
-- background, problem_statement: 자유 텍스트 (선택).
ALTER TABLE batch_categories ADD COLUMN purpose VARCHAR(32);
ALTER TABLE batch_categories ADD COLUMN background TEXT;
ALTER TABLE batch_categories ADD COLUMN problem_statement TEXT;
ALTER TABLE batch_categories ADD CONSTRAINT chk_batch_categories_purpose
  CHECK (purpose IS NULL OR purpose IN
    ('SALES', 'RESEARCH', 'COMPETITIVE', 'CUSTOMER_CARE', 'OTHER'));
