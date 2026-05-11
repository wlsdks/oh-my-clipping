-- ============================================================
-- V116: 같은 사용자가 같은 이름의 페르소나/카테고리를 여러 개 소유한 경우 중복 제거
--
-- 배경:
--   DB 감사 중 한 사용자가 "기본 요약 스타일" 페르소나 4개, "운영 체크 요약
--   잔잔한 협업 브리핑" 페르소나 2개, "물류 동향 브리핑 푸른 협업 브리핑"
--   카테고리 2개를 동시에 소유하고 있음을 확인했다. 생성 플로우가 idempotent
--   하지 않아 같은 입력이 반복 제출되면 새 row 가 계속 쌓인다.
--
-- 처리:
--   1) user_owned_personas 에서 같은 (user_id, persona.name) 그룹 중 가장
--      오래된 소유 관계만 남기고 나머지는 삭제한다. 페르소나 본체(clipping_
--      personas) 는 다른 참조(weekly_persona_snapshot 등)가 있을 수 있고
--      CASCADE 정책이 혼재하므로 건드리지 않는다. UI 는 user_owned 를
--      경유해 조회하므로 사용자 입장에서는 중복이 사라진다.
--   2) user_owned_categories 에 동일한 전략을 적용한다.
--
-- idempotency:
--   ROW_NUMBER() > 1 조건 덕분에 이미 dedup 된 상태에서는 삭제 대상이 0이라
--   재실행에도 안전하다.
--
-- scope:
--   is_preset = true 인 시스템 프리셋 페르소나의 소유 관계는 건드리지 않는다.
-- ============================================================

-- CTE + DELETE 연결은 H2 에서 지원되지 않아 서브쿼리 인라인 + 단일컬럼 IN 로
-- 재작성한다. (H2 MODE=PostgreSQL 에서도 (tuple) IN 서브쿼리는 제한적이라
-- 관계형 참조 컬럼 `uop_ctid`/`uoc_ctid` 대신 각 테이블의 PK 에 해당하는
-- rowkey 를 조합한 문자열 key 로 대상 row 만 식별한다.)

-- 1. 페르소나 소유 관계 dedup: 같은 (user_id, persona.name) 그룹에서 가장
--    오래된 하나만 남기고 나머지 row 를 삭제한다.
DELETE FROM clipping_user_owned_personas
WHERE (user_id || '|' || persona_id) IN (
    SELECT (user_id || '|' || persona_id)
    FROM (
        SELECT
            o.user_id,
            o.persona_id,
            ROW_NUMBER() OVER (
                PARTITION BY o.user_id, p.name
                ORDER BY p.created_at, o.created_at, o.persona_id
            ) AS rn
        FROM clipping_user_owned_personas o
        JOIN clipping_personas p ON p.id = o.persona_id
        WHERE p.is_preset = false
    ) dup
    WHERE dup.rn > 1
);

-- 2. 카테고리 소유 관계 dedup
DELETE FROM clipping_user_owned_categories
WHERE (user_id || '|' || category_id) IN (
    SELECT (user_id || '|' || category_id)
    FROM (
        SELECT
            o.user_id,
            o.category_id,
            ROW_NUMBER() OVER (
                PARTITION BY o.user_id, c.name
                ORDER BY c.created_at, o.category_id
            ) AS rn
        FROM clipping_user_owned_categories o
        JOIN batch_categories c ON c.id = o.category_id
    ) dup
    WHERE dup.rn > 1
);
