-- ============================================================
-- V100: 시스템 프리셋 프롬프트 복구 + delivery_log → batch_categories FK 추가
--
-- 배경:
--   1) E2E 테스트가 시스템 프리셋 #2("핵심 요약"), #3("깊이 있는 분석")의
--      system_prompt를 "E2E 수정 테스트 프롬프트입니다." 로 덮어쓴 채
--      teardown 없이 종료되어, 로컬 DB의 해당 프리셋이 오염된 상태였다.
--      V86 원본 값으로 재적용한다. 운영 DB에 이미 올바른 값이 있다면 no-op.
--   2) delivery_log.category_id가 batch_categories(id)를 참조하지만
--      외래키 제약이 없어 category 삭제 시 orphan 로그가 남았다.
--      orphan 행을 정리한 뒤 ON DELETE CASCADE FK를 추가한다.
-- ============================================================

-- 1. 시스템 프리셋 #2 "핵심 요약" 프롬프트 복구 (V86 원본)
UPDATE clipping_personas SET
  system_prompt = '당신은 뉴스 에디터입니다. 독자는 바쁜 직장인입니다.

규칙:
- 한 문장에 하나의 요점만 담는다.
- 전문 용어가 나오면 괄호 안에 한 줄 설명을 붙인다.
- 원문을 그대로 옮기지 않고 핵심만 재구성한다.
- 각 줄은 40~60자 이내로 작성한다.
- 추측성 전망이나 감정적 표현을 넣지 않는다.

출력 형식:
📌 핵심: (무슨 일이 일어났는가 — 2~3줄)
💡 왜 중요한가: (나와 우리 회사에 어떤 의미인가 — 1~2줄)
👉 한 줄 요약: (기사 전체를 한 문장으로 — 1줄)',
  updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000002'
  AND system_prompt = 'E2E 수정 테스트 프롬프트입니다.';

-- 2. 시스템 프리셋 #3 "깊이 있는 분석" 프롬프트 복구 (V86 원본)
UPDATE clipping_personas SET
  system_prompt = '당신은 시니어 산업 애널리스트입니다. 배경과 맥락을 포함해 분석합니다.

규칙:
- 전문 용어는 첫 등장 시 괄호 안에 설명을 붙인다.
- 인과관계를 명확히 드러낸다 (~때문에, ~의 결과로).
- 확인된 사실과 전망/추정을 구분한다.
- 원문을 그대로 인용하지 않는다.
- 각 줄은 50~70자 이내로 작성한다.

출력 형식:
🔍 배경: (이 이슈가 왜 나왔는가 — 2줄)
📊 핵심 내용: (무엇이 확인되었는가 — 2~3줄)
📈 영향 분석: (산업·시장·이해관계자에게 미치는 파급 — 2줄)
🔮 전망: (향후 어떻게 전개될 가능성이 있는가 — 1줄)',
  updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000003'
  AND system_prompt = 'E2E 수정 테스트 프롬프트입니다.';

-- 3. delivery_log orphan 정리: 참조하는 batch_categories가 없는 행 삭제
DELETE FROM delivery_log
WHERE category_id NOT IN (SELECT id FROM batch_categories);

-- 4. delivery_log → batch_categories FK 추가 (카테고리 삭제 시 로그도 삭제)
--    idempotent: 이미 존재하면 DROP 후 재생성하여 재실행 안전성을 보장한다.
ALTER TABLE delivery_log DROP CONSTRAINT IF EXISTS fk_delivery_log_category;
ALTER TABLE delivery_log
  ADD CONSTRAINT fk_delivery_log_category
  FOREIGN KEY (category_id)
  REFERENCES batch_categories (id)
  ON DELETE CASCADE;
