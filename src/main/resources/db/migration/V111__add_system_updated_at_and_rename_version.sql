-- V111: 낙관적 잠금(옵션 B) 기반 설계 적용
-- 1) Persona / Category / CategoryRule / RssSource 4개 테이블에 system_updated_at TIMESTAMP 컬럼 추가.
--    기존 updated_at은 "사용자가 의미 있게 수정한 시각"으로 역할을 제한하고,
--    system_updated_at은 스케줄러/크롤러가 갱신하는 시스템 상태 변경 시각으로 분리한다.
-- 2) clipping_category_rules.version 컬럼을 revision으로 리네이밍.
--    API 응답의 version 필드는 @Deprecated로 호환성만 유지하고, revision이 권위 있는 이름이다.
--
-- H2와 PostgreSQL 모두 지원하는 ANSI SQL만 사용한다.
-- nullable 컬럼 추가 후 UPDATE로 초기화, 마지막에 SET NOT NULL 제약 추가 (2단계 분할).

-- 1) clipping_personas: system_updated_at 추가 (신규 INSERT용 DEFAULT + NOT NULL)
ALTER TABLE clipping_personas ADD COLUMN system_updated_at TIMESTAMP;
UPDATE clipping_personas SET system_updated_at = updated_at WHERE system_updated_at IS NULL;
ALTER TABLE clipping_personas ALTER COLUMN system_updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE clipping_personas ALTER COLUMN system_updated_at SET NOT NULL;

-- 2) batch_categories: system_updated_at 추가
ALTER TABLE batch_categories ADD COLUMN system_updated_at TIMESTAMP;
UPDATE batch_categories SET system_updated_at = updated_at WHERE system_updated_at IS NULL;
ALTER TABLE batch_categories ALTER COLUMN system_updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE batch_categories ALTER COLUMN system_updated_at SET NOT NULL;

-- 3) rss_sources: system_updated_at 추가
ALTER TABLE rss_sources ADD COLUMN system_updated_at TIMESTAMP;
UPDATE rss_sources SET system_updated_at = updated_at WHERE system_updated_at IS NULL;
ALTER TABLE rss_sources ALTER COLUMN system_updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE rss_sources ALTER COLUMN system_updated_at SET NOT NULL;

-- 4) clipping_category_rules: system_updated_at 추가 + version → revision 리네이밍
ALTER TABLE clipping_category_rules ADD COLUMN system_updated_at TIMESTAMP;
UPDATE clipping_category_rules SET system_updated_at = updated_at WHERE system_updated_at IS NULL;
ALTER TABLE clipping_category_rules ALTER COLUMN system_updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE clipping_category_rules ALTER COLUMN system_updated_at SET NOT NULL;

-- version → revision. H2와 PostgreSQL 모두 ALTER TABLE ... RENAME COLUMN을 지원한다.
ALTER TABLE clipping_category_rules RENAME COLUMN version TO revision;
