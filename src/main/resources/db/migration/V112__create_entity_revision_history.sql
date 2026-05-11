-- V112: 엔티티 변경 이력 통합 테이블 생성 (F1 PR 4/5).
-- 4개 도메인(Persona, Category, CategoryRule, RssSource) 수정 이력을 한 테이블에 append-only로 저장해
-- "변경 이력" UI와 "이 버전으로 되돌리기" 기능의 공통 저장소 역할을 한다.
--
-- 설계 원칙:
--  - resource_type + resource_id 조합이 식별자. revision_number는 조합 단위로 1부터 증가.
--  - snapshot은 업데이트 직후 엔티티의 JSON 직렬화(TEXT) — 검색은 하지 않으므로 JSONB 불필요.
--  - changed_fields는 JSON array text — StaleEditInfo.changedFieldNames 계산과 동일 로직을 재사용한다.
--  - editor_display_name은 익명화된 표시 이름(없으면 null, 프론트에서 "관리자"로 표시).
--  - Retention은 Phase 2 F9(DataCleanupScheduler 확장)에서 처리하므로 이 PR에서는 cleanup 없음.
-- H2/PostgreSQL 모두 지원하는 ANSI SQL 만 사용한다.

CREATE TABLE entity_revision_history (
    id VARCHAR(36) PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    revision_number BIGINT NOT NULL,
    editor_id VARCHAR(100) NOT NULL,
    editor_display_name VARCHAR(200),
    changed_fields TEXT,
    snapshot TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 리소스 단위 조회 (history 목록, latest revision 계산) — DESC로 최신부터 읽는다.
CREATE INDEX idx_entity_revision_resource
    ON entity_revision_history (resource_type, resource_id, revision_number DESC);

-- retention cleanup (Phase 2)을 위한 시간 인덱스.
CREATE INDEX idx_entity_revision_created
    ON entity_revision_history (created_at);
