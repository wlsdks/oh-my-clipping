-- 경쟁사 수집 전용 카테고리. is_active=FALSE + status=PAUSED로 일반 다이제스트에서 제외한다.
-- H2/PostgreSQL 호환: 존재하지 않을 때만 삽입
INSERT INTO batch_categories (id, name, description, is_active, status)
SELECT '__competitor__', '경쟁사 뉴스', '경쟁사 수집 파이프라인 전용 카테고리 (다이제스트 대상 아님)', FALSE, 'PAUSED'
WHERE NOT EXISTS (SELECT 1 FROM batch_categories WHERE id = '__competitor__');
