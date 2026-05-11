-- Local-only dev bootstrap data
-- 고정 ID row만 재적용해 로그인/대시보드 점검 데이터를 맞춘다.

DELETE FROM summary_feedback
WHERE id IN (
    '00000000-0000-0000-0000-000000000901',
    '00000000-0000-0000-0000-000000000902',
    '00000000-0000-0000-0000-000000000903',
    '00000000-0000-0000-0000-000000000904'
);

DELETE FROM clipping_review_item_audits
WHERE id IN (
    '00000000-0000-0000-0000-000000000601',
    '00000000-0000-0000-0000-000000000602',
    '00000000-0000-0000-0000-000000000603',
    '00000000-0000-0000-0000-000000000604',
    '00000000-0000-0000-0000-000000000605',
    '00000000-0000-0000-0000-000000000606'
);

DELETE FROM clipping_review_items
WHERE summary_id IN (
    '00000000-0000-0000-0000-000000000501',
    '00000000-0000-0000-0000-000000000502',
    '00000000-0000-0000-0000-000000000503',
    '00000000-0000-0000-0000-000000000504',
    '00000000-0000-0000-0000-000000000505',
    '00000000-0000-0000-0000-000000000506'
);

DELETE FROM batch_summaries
WHERE id IN (
    '00000000-0000-0000-0000-000000000501',
    '00000000-0000-0000-0000-000000000502',
    '00000000-0000-0000-0000-000000000503',
    '00000000-0000-0000-0000-000000000504',
    '00000000-0000-0000-0000-000000000505',
    '00000000-0000-0000-0000-000000000506',
    '00000000-0000-0000-0000-000000000507',
    '00000000-0000-0000-0000-000000000508',
    '00000000-0000-0000-0000-000000000511',
    '00000000-0000-0000-0000-000000000512',
    '00000000-0000-0000-0000-000000000513',
    '00000000-0000-0000-0000-000000000514',
    '00000000-0000-0000-0000-000000000515',
    '00000000-0000-0000-0000-000000000516',
    '00000000-0000-0000-0000-000000000517',
    '00000000-0000-0000-0000-000000000518',
    '00000000-0000-0000-0000-000000000519',
    '00000000-0000-0000-0000-000000000520',
    '00000000-0000-0000-0000-000000000521',
    '00000000-0000-0000-0000-000000000522',
    '00000000-0000-0000-0000-000000000531',
    '00000000-0000-0000-0000-000000000532',
    '00000000-0000-0000-0000-000000000533',
    '00000000-0000-0000-0000-000000000534',
    '00000000-0000-0000-0000-000000000535',
    '00000000-0000-0000-0000-000000000536',
    '00000000-0000-0000-0000-000000000537',
    '00000000-0000-0000-0000-000000000538',
    '00000000-0000-0000-0000-000000000539',
    '00000000-0000-0000-0000-000000000540',
    '00000000-0000-0000-0000-000000000541',
    '00000000-0000-0000-0000-000000000542',
    '00000000-0000-0000-0000-000000000543',
    '00000000-0000-0000-0000-000000000544',
    '00000000-0000-0000-0000-000000000545',
    '00000000-0000-0000-0000-000000000546',
    '00000000-0000-0000-0000-000000000547',
    '00000000-0000-0000-0000-000000000548',
    '00000000-0000-0000-0000-000000000549',
    '00000000-0000-0000-0000-000000000550'
);

DELETE FROM batch_summaries
WHERE rss_item_id IN (
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000402',
    '00000000-0000-0000-0000-000000000403',
    '00000000-0000-0000-0000-000000000404',
    '00000000-0000-0000-0000-000000000405',
    '00000000-0000-0000-0000-000000000406',
    '00000000-0000-0000-0000-000000000407',
    '00000000-0000-0000-0000-000000000408',
    '00000000-0000-0000-0000-000000000511',
    '00000000-0000-0000-0000-000000000512',
    '00000000-0000-0000-0000-000000000513',
    '00000000-0000-0000-0000-000000000514',
    '00000000-0000-0000-0000-000000000515',
    '00000000-0000-0000-0000-000000000516',
    '00000000-0000-0000-0000-000000000517',
    '00000000-0000-0000-0000-000000000518',
    '00000000-0000-0000-0000-000000000519',
    '00000000-0000-0000-0000-000000000520',
    '00000000-0000-0000-0000-000000000521',
    '00000000-0000-0000-0000-000000000522',
    '00000000-0000-0000-0000-000000000531',
    '00000000-0000-0000-0000-000000000532',
    '00000000-0000-0000-0000-000000000533',
    '00000000-0000-0000-0000-000000000534',
    '00000000-0000-0000-0000-000000000535',
    '00000000-0000-0000-0000-000000000536',
    '00000000-0000-0000-0000-000000000537',
    '00000000-0000-0000-0000-000000000538',
    '00000000-0000-0000-0000-000000000539',
    '00000000-0000-0000-0000-000000000540',
    '00000000-0000-0000-0000-000000000541',
    '00000000-0000-0000-0000-000000000542',
    '00000000-0000-0000-0000-000000000543',
    '00000000-0000-0000-0000-000000000544',
    '00000000-0000-0000-0000-000000000545',
    '00000000-0000-0000-0000-000000000546',
    '00000000-0000-0000-0000-000000000547',
    '00000000-0000-0000-0000-000000000548',
    '00000000-0000-0000-0000-000000000549',
    '00000000-0000-0000-0000-000000000550'
);

DELETE FROM rss_items
WHERE id IN (
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000402',
    '00000000-0000-0000-0000-000000000403',
    '00000000-0000-0000-0000-000000000404',
    '00000000-0000-0000-0000-000000000405',
    '00000000-0000-0000-0000-000000000406',
    '00000000-0000-0000-0000-000000000407',
    '00000000-0000-0000-0000-000000000408'
);

DELETE FROM clipping_stats
WHERE id IN (
    '00000000-0000-0000-0000-000000000801',
    '00000000-0000-0000-0000-000000000802',
    '00000000-0000-0000-0000-000000000803',
    '00000000-0000-0000-0000-000000000804',
    '00000000-0000-0000-0000-000000000805',
    '00000000-0000-0000-0000-000000000806',
    '00000000-0000-0000-0000-000000000807',
    '00000000-0000-0000-0000-000000000808',
    '00000000-0000-0000-0000-000000000809'
);

DELETE FROM clipping_user_requests
WHERE id IN (
    '00000000-0000-0000-0000-000000001001',
    '00000000-0000-0000-0000-000000001002',
    '00000000-0000-0000-0000-000000001003',
    '00000000-0000-0000-0000-000000001004',
    '00000000-0000-0000-0000-000000001005',
    '00000000-0000-0000-0000-000000001006',
    '00000000-0000-0000-0000-000000001007',
    '00000000-0000-0000-0000-000000001008'
);

-- 0) 로컬 점검/E2E가 남긴 사용자 생성 데이터 정리
DROP TABLE IF EXISTS tmp_local_cleanup_summaries;
DROP TABLE IF EXISTS tmp_local_cleanup_rss_items;
DROP TABLE IF EXISTS tmp_local_cleanup_sources;
DROP TABLE IF EXISTS tmp_local_cleanup_categories;
DROP TABLE IF EXISTS tmp_local_cleanup_personas;
DROP TABLE IF EXISTS tmp_local_cleanup_requests;

CREATE TEMPORARY TABLE tmp_local_cleanup_requests AS
SELECT id, approved_category_id, approved_source_id, approved_persona_id
FROM clipping_user_requests
WHERE id NOT IN (
    '00000000-0000-0000-0000-000000001001',
    '00000000-0000-0000-0000-000000001002',
    '00000000-0000-0000-0000-000000001003'
);

CREATE TEMPORARY TABLE tmp_local_cleanup_categories AS
SELECT DISTINCT category_id AS id
FROM clipping_user_owned_categories
WHERE category_id NOT LIKE '00000000-%'
UNION
SELECT DISTINCT approved_category_id
FROM tmp_local_cleanup_requests
WHERE approved_category_id IS NOT NULL
  AND approved_category_id NOT LIKE '00000000-%'
UNION
SELECT DISTINCT id
FROM batch_categories
WHERE name LIKE '%Playwright%'
   OR name LIKE '%MCP%'
   OR name LIKE 'DM 주제 %'
   OR name LIKE '빠른세팅% 뉴스'
   OR name LIKE '기능검증 %'
   OR name LIKE 'QS %'
   OR name LIKE '온보딩 간편 시작 %'
UNION
SELECT DISTINCT id
FROM batch_categories
WHERE id NOT IN (
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000206',
    '00000000-0000-0000-0000-000000000207',
    '00000000-0000-0000-0000-000000000208',
    '00000000-0000-0000-0000-000000000209',
    '00000000-0000-0000-0000-000000000210'
)
AND name IN (
    'AI/테크',
    'HR/L&D',
    '투자/금융',
    '정책/규제',
    '보안/인프라',
    '마케팅/이커머스'
);

CREATE TEMPORARY TABLE tmp_local_cleanup_sources AS
SELECT DISTINCT source_id AS id
FROM clipping_user_owned_sources
WHERE source_id NOT LIKE '00000000-%'
UNION
SELECT DISTINCT approved_source_id
FROM tmp_local_cleanup_requests
WHERE approved_source_id IS NOT NULL
  AND approved_source_id NOT LIKE '00000000-%'
UNION
SELECT DISTINCT id
FROM rss_sources
WHERE category_id IN (SELECT id FROM tmp_local_cleanup_categories)
UNION
SELECT DISTINCT id
FROM rss_sources
WHERE name LIKE '%Playwright%'
   OR name LIKE '%MCP%';

CREATE TEMPORARY TABLE tmp_local_cleanup_rss_items AS
SELECT DISTINCT id
FROM rss_items
WHERE category_id IN (SELECT id FROM tmp_local_cleanup_categories)
   OR rss_source_id IN (SELECT id FROM tmp_local_cleanup_sources);

CREATE TEMPORARY TABLE tmp_local_cleanup_summaries AS
SELECT DISTINCT id
FROM batch_summaries
WHERE category_id IN (SELECT id FROM tmp_local_cleanup_categories)
   OR rss_item_id IN (SELECT id FROM tmp_local_cleanup_rss_items);

CREATE TEMPORARY TABLE tmp_local_cleanup_personas AS
SELECT DISTINCT persona_id AS id
FROM clipping_user_owned_personas
WHERE persona_id NOT LIKE '00000000-%'
UNION
SELECT DISTINCT approved_persona_id
FROM tmp_local_cleanup_requests
WHERE approved_persona_id IS NOT NULL
  AND approved_persona_id NOT LIKE '00000000-%'
UNION
SELECT DISTINCT id
FROM clipping_personas
WHERE name LIKE '%Playwright%'
   OR name LIKE '%MCP%'
   OR name LIKE 'DM 스타일 %';

DELETE FROM summary_feedback
WHERE summary_id IN (SELECT id FROM tmp_local_cleanup_summaries);

DELETE FROM clipping_review_item_audits
WHERE summary_id IN (SELECT id FROM tmp_local_cleanup_summaries)
   OR category_id IN (SELECT id FROM tmp_local_cleanup_categories);

DELETE FROM clipping_review_items
WHERE summary_id IN (SELECT id FROM tmp_local_cleanup_summaries)
   OR category_id IN (SELECT id FROM tmp_local_cleanup_categories);

DELETE FROM batch_summaries
WHERE id IN (SELECT id FROM tmp_local_cleanup_summaries);

DELETE FROM daily_summaries
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM daily_summaries
WHERE id IN (
    '00000000-0000-0000-0000-000000000701',
    '00000000-0000-0000-0000-000000000702',
    '00000000-0000-0000-0000-000000000703',
    '00000000-0000-0000-0000-000000000704',
    '00000000-0000-0000-0000-000000000705',
    '00000000-0000-0000-0000-000000000706',
    '00000000-0000-0000-0000-000000000707',
    '00000000-0000-0000-0000-000000000708',
    '00000000-0000-0000-0000-000000000709',
    '00000000-0000-0000-0000-000000000710',
    '00000000-0000-0000-0000-000000000711',
    '00000000-0000-0000-0000-000000000712'
);

DELETE FROM competitor_watchlist
WHERE id IN (
    '00000000-0000-0000-0000-000000000801',
    '00000000-0000-0000-0000-000000000802',
    '00000000-0000-0000-0000-000000000803',
    '00000000-0000-0000-0000-000000000804',
    '00000000-0000-0000-0000-000000000805'
);

-- E2E 테스트가 누적 생성한 경쟁사 정리 (이름이 'E2E'로 시작하는 항목)
-- E2E 실행마다 새 경쟁사가 추가돼 MAX_COMPETITORS(20) 한도에 도달하면
-- 다음 E2E 테스트가 HTTP 400 으로 실패한다.
DELETE FROM competitor_watchlist WHERE name LIKE 'E2E%';
-- 동기화된 organizations 항목도 함께 정리한다.
DELETE FROM organizations WHERE name LIKE 'E2E%' AND type = 'COMPETITOR';

DELETE FROM llm_runs
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM pipeline_step_traces
WHERE run_id IN (
    SELECT id
    FROM pipeline_runs
    WHERE category_id IN (
        SELECT id
        FROM tmp_local_cleanup_categories
    )
);

DELETE FROM pipeline_runs
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM clipping_stats
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM clipping_trend_snapshots
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM clipping_retention_policies
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

-- clipping_category_region_policies 테이블은 V56에서 삭제됨

DELETE FROM clipping_category_rules
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM pipeline_runs
WHERE category_id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM batch_summaries
WHERE rss_item_id IN (SELECT id FROM tmp_local_cleanup_rss_items)
   OR rss_item_id IN (
       SELECT ri.id FROM rss_items ri
       WHERE ri.rss_source_id IN (SELECT id FROM tmp_local_cleanup_sources)
          OR ri.category_id IN (SELECT id FROM tmp_local_cleanup_categories)
   );

DELETE FROM rss_items
WHERE id IN (SELECT id FROM tmp_local_cleanup_rss_items)
   OR rss_source_id IN (SELECT id FROM tmp_local_cleanup_sources)
   OR category_id IN (SELECT id FROM tmp_local_cleanup_categories);

DELETE FROM clipping_user_requests
WHERE id IN (SELECT id FROM tmp_local_cleanup_requests);

DELETE FROM clipping_user_owned_sources
WHERE source_id IN (SELECT id FROM tmp_local_cleanup_sources);

DELETE FROM rss_sources
WHERE id IN (
    SELECT id
    FROM tmp_local_cleanup_sources
);

DELETE FROM clipping_user_owned_categories
WHERE category_id IN (SELECT id FROM tmp_local_cleanup_categories);

DELETE FROM batch_categories
WHERE id IN (
    SELECT id
    FROM tmp_local_cleanup_categories
);

DELETE FROM clipping_user_owned_personas
WHERE persona_id IN (SELECT id FROM tmp_local_cleanup_personas);

DELETE FROM clipping_personas p
WHERE p.id IN (
    SELECT id
    FROM tmp_local_cleanup_personas
)
  AND NOT EXISTS (
      SELECT 1
      FROM batch_categories c
      WHERE c.persona_id = p.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM clipping_user_requests r
      WHERE r.approved_persona_id = p.id
  );

DROP TABLE IF EXISTS tmp_local_cleanup_summaries;
DROP TABLE IF EXISTS tmp_local_cleanup_rss_items;
DROP TABLE IF EXISTS tmp_local_cleanup_sources;
DROP TABLE IF EXISTS tmp_local_cleanup_categories;
DROP TABLE IF EXISTS tmp_local_cleanup_personas;
DROP TABLE IF EXISTS tmp_local_cleanup_requests;

-- 0-a) 프리셋 페르소나 보장 — 마이그레이션이 누락되거나 DB 리셋 후에도 항상 존재하게 한다
UPDATE clipping_personas
SET name            = '경영진 브리핑',
    description     = '의사결정에 필요한 정보만',
    system_prompt   = '당신은 C-레벨 임원의 수석 비서입니다. 보고서를 작성하듯 간결하게 작성합니다.

규칙:
- 업계 전문 용어는 그대로 사용한다.
- 수치와 팩트 중심으로 작성한다.
- 추측성 전망이나 감정적 표현을 넣지 않는다.
- 원문을 그대로 인용하지 않는다.
- 각 줄은 50자 이내로 작성한다.

출력 형식:
▸ 핵심 팩트 (무엇이 확인되었는가 — 2줄)
▸ 비즈니스 임팩트 (매출·비용·경쟁·규제에 미치는 영향 — 1줄)
▸ 의사결정 포인트 (검토하거나 지시해야 할 사항 — 1줄)',
    summary_style   = '핵심 2줄 + 비즈니스 임팩트 1줄',
    target_audience = '경영진·임원',
    max_items       = 5,
    language        = 'ko',
    is_active       = TRUE,
    is_preset       = TRUE,
    preview_title   = 'AI 교육 시장, 2026년 글로벌 120조 원 돌파 전망',
    preview_source  = 'Example Business Daily · 30분 전',
    preview_body    = '▸ 핵심 팩트: 글로벌 AI 교육 시장 규모가 2026년 120조 원을 돌파할 전망. 기업교육(B2B) 세그먼트가 전체 성장의 65%를 견인.

▸ 비즈니스 임팩트: AI 기반 교육 상품 라인업 확대가 시급. 경쟁사 대비 선점 여부가 중기 매출에 직결.

▸ 의사결정 포인트: AI 교육 상품 로드맵 및 투자 우선순위 재검토가 필요합니다.',
    updated_at      = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000001';

INSERT INTO clipping_personas (
    id, name, description, system_prompt, summary_style, target_audience,
    max_items, language, is_active, is_preset,
    preview_title, preview_source, preview_body, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000001',
    '경영진 브리핑',
    '의사결정에 필요한 정보만',
    '당신은 C-레벨 임원의 수석 비서입니다. 보고서를 작성하듯 간결하게 작성합니다.

규칙:
- 업계 전문 용어는 그대로 사용한다.
- 수치와 팩트 중심으로 작성한다.
- 추측성 전망이나 감정적 표현을 넣지 않는다.
- 원문을 그대로 인용하지 않는다.
- 각 줄은 50자 이내로 작성한다.

출력 형식:
▸ 핵심 팩트 (무엇이 확인되었는가 — 2줄)
▸ 비즈니스 임팩트 (매출·비용·경쟁·규제에 미치는 영향 — 1줄)
▸ 의사결정 포인트 (검토하거나 지시해야 할 사항 — 1줄)',
    '핵심 2줄 + 비즈니스 임팩트 1줄',
    '경영진·임원',
    5, 'ko', TRUE, TRUE,
    'AI 교육 시장, 2026년 글로벌 120조 원 돌파 전망',
    'Example Business Daily · 30분 전',
    '▸ 핵심 팩트: 글로벌 AI 교육 시장 규모가 2026년 120조 원을 돌파할 전망. 기업교육(B2B) 세그먼트가 전체 성장의 65%를 견인.

▸ 비즈니스 임팩트: AI 기반 교육 상품 라인업 확대가 시급. 경쟁사 대비 선점 여부가 중기 매출에 직결.

▸ 의사결정 포인트: AI 교육 상품 로드맵 및 투자 우선순위 재검토가 필요합니다.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM clipping_personas WHERE id = '00000000-0000-0000-0000-000000000001'
);

UPDATE clipping_personas
SET name            = '핵심 요약',
    description     = '바쁜 하루, 1분이면 충분합니다',
    system_prompt   = '당신은 뉴스 에디터입니다. 독자는 바쁜 직장인입니다.

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
    summary_style   = '핵심 2~3줄 + 왜 중요한가 1~2줄 + 한 줄 요약',
    target_audience = '전 직원 (기본 프리셋)',
    max_items       = 5,
    language        = 'ko',
    is_active       = TRUE,
    is_preset       = TRUE,
    preview_title   = '직장인 재교육 의무화 법안, 국회 본회의 통과',
    preview_source  = 'Example Economic Times · 1시간 전',
    preview_body    = '📌 핵심: 종업원 100인 이상 기업은 연간 직무교육 40시간 이상을 의무 제공해야 하는 법안이 국회를 통과했어요. 2027년부터 시행되며, 위반 시 과태료가 부과됩니다.

💡 왜 중요한가: 기업교육 시장이 구조적으로 확대될 전환점이에요. 의무교육 콘텐츠 수요가 크게 늘어날 수 있습니다.

👉 한 줄 요약: 기업 재교육 의무화로 교육 시장의 판이 커집니다.',
    updated_at      = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000002';

INSERT INTO clipping_personas (
    id, name, description, system_prompt, summary_style, target_audience,
    max_items, language, is_active, is_preset,
    preview_title, preview_source, preview_body, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000002',
    '핵심 요약',
    '바쁜 하루, 1분이면 충분합니다',
    '당신은 뉴스 에디터입니다. 독자는 바쁜 직장인입니다.

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
    '핵심 2~3줄 + 왜 중요한가 1~2줄 + 한 줄 요약',
    '전 직원 (기본 프리셋)',
    5, 'ko', TRUE, TRUE,
    '직장인 재교육 의무화 법안, 국회 본회의 통과',
    'Example Economic Times · 1시간 전',
    '📌 핵심: 종업원 100인 이상 기업은 연간 직무교육 40시간 이상을 의무 제공해야 하는 법안이 국회를 통과했어요. 2027년부터 시행되며, 위반 시 과태료가 부과됩니다.

💡 왜 중요한가: 기업교육 시장이 구조적으로 확대될 전환점이에요. 의무교육 콘텐츠 수요가 크게 늘어날 수 있습니다.

👉 한 줄 요약: 기업 재교육 의무화로 교육 시장의 판이 커집니다.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM clipping_personas WHERE id = '00000000-0000-0000-0000-000000000002'
);

UPDATE clipping_personas
SET name            = '깊이 있는 분석',
    description     = '맥락과 전망까지 한눈에',
    system_prompt   = '당신은 시니어 산업 애널리스트입니다. 배경과 맥락을 포함해 분석합니다.

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
    summary_style   = '배경 2줄 + 핵심 내용 3줄 + 영향 2줄 + 전망 1줄',
    target_audience = '전략/기획, 리서치, 심층 이해가 필요한 분',
    max_items       = 5,
    language        = 'ko',
    is_active       = TRUE,
    is_preset       = TRUE,
    preview_title   = '글로벌 EdTech, M&A 물결... 2025년 거래액 사상 최대',
    preview_source  = 'Example Business Daily · 2시간 전',
    preview_body    = '🔍 배경: 코로나 이후 급성장한 EdTech 시장이 성숙기에 접어들며 통합(consolidation) 국면에 진입했습니다. 규모의 경제를 확보하려는 대형 플레이어들의 인수 경쟁이 치열해지고 있어요.

📊 핵심 내용: 2025년 글로벌 EdTech M&A 거래액이 280억 달러로 사상 최대치를 기록. 특히 B2B 기업교육 분야에서 AI 기반 스타트업 인수가 집중되고 있습니다.

📈 영향 분석: 국내 EdTech 기업의 밸류에이션에도 영향. 기술력 보유 스타트업의 몸값이 상승하며, 자체 개발과 인수 사이의 전략적 판단이 중요해졌습니다.

🔮 전망: 하반기 국내에서도 중대형 M&A 발표가 예상되며, 플랫폼 통합 경쟁이 본격화될 수 있습니다.',
    updated_at      = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000003';

INSERT INTO clipping_personas (
    id, name, description, system_prompt, summary_style, target_audience,
    max_items, language, is_active, is_preset,
    preview_title, preview_source, preview_body, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000003',
    '깊이 있는 분석',
    '맥락과 전망까지 한눈에',
    '당신은 시니어 산업 애널리스트입니다. 배경과 맥락을 포함해 분석합니다.

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
    '배경 2줄 + 핵심 내용 3줄 + 영향 2줄 + 전망 1줄',
    '전략/기획, 리서치, 심층 이해가 필요한 분',
    5, 'ko', TRUE, TRUE,
    '글로벌 EdTech, M&A 물결... 2025년 거래액 사상 최대',
    'Example Business Daily · 2시간 전',
    '🔍 배경: 코로나 이후 급성장한 EdTech 시장이 성숙기에 접어들며 통합(consolidation) 국면에 진입했습니다. 규모의 경제를 확보하려는 대형 플레이어들의 인수 경쟁이 치열해지고 있어요.

📊 핵심 내용: 2025년 글로벌 EdTech M&A 거래액이 280억 달러로 사상 최대치를 기록. 특히 B2B 기업교육 분야에서 AI 기반 스타트업 인수가 집중되고 있습니다.

📈 영향 분석: 국내 EdTech 기업의 밸류에이션에도 영향. 기술력 보유 스타트업의 몸값이 상승하며, 자체 개발과 인수 사이의 전략적 판단이 중요해졌습니다.

🔮 전망: 하반기 국내에서도 중대형 M&A 발표가 예상되며, 플랫폼 통합 경쟁이 본격화될 수 있습니다.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM clipping_personas WHERE id = '00000000-0000-0000-0000-000000000003'
);

UPDATE clipping_personas
SET name            = '교육 트렌드 분석',
    description     = '교육·HRD 관점으로 읽는 뉴스',
    system_prompt   = '당신은 B2B 기업교육(HRD) 전문 애널리스트입니다. 모든 뉴스를 교육 산업 관점에서 해석합니다.

규칙:
- 교육/HRD 업계 용어(마이크로러닝, LMS, 역량 개발 등)를 자연스럽게 사용한다.
- 뉴스가 교육과 직접 관련이 없더라도, 기업교육 시장에 미칠 간접 영향을 분석한다.
- 구체적인 적용 아이디어를 포함한다.
- 원문을 그대로 인용하지 않는다.
- 각 줄은 50~70자 이내로 작성한다.

출력 형식:
📰 변화 포인트: (무엇이 바뀌고 있는가 — 2줄)
🎓 교육 시사점: (교육/HRD 업계에 어떤 의미인가 — 2줄)
💡 적용 아이디어: (교육 상품·서비스·콘텐츠에 어떻게 활용할 수 있는가 — 1줄)',
    summary_style   = '변화 포인트 2줄 + 교육 시사점 2줄 + 적용 아이디어 1줄',
    target_audience = 'HRD 담당자, 교육 기획자, 콘텐츠 개발자',
    max_items       = 5,
    language        = 'ko',
    is_active       = TRUE,
    is_preset       = TRUE,
    preview_title   = 'AI 튜터링 기술, 대기업 신입사원 교육에 본격 도입',
    preview_source  = 'Example Tech Daily · 1시간 전',
    preview_body    = '📰 변화 포인트: MegaCorp, ConglomerateCo 등 대기업이 신입사원 온보딩에 AI 튜터 시스템을 도입하기 시작했어요. 기존 집합교육 대비 학습 완료율이 35% 높고, 교육 담당자 업무량이 절반으로 줄었다는 시범 결과가 나왔습니다.

🎓 교육 시사점: AI 튜터링이 보조 도구에서 메인 교육 채널로 격상되는 신호예요. 특히 반복 학습, 실습 피드백 영역에서 기존 이러닝을 빠르게 대체할 수 있습니다.

💡 적용 아이디어: 기존 온보딩 콘텐츠를 AI 튜터 대화형으로 재설계하면, 고객사에 차별화된 교육 솔루션을 제안할 수 있어요.',
    updated_at      = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000004';

INSERT INTO clipping_personas (
    id, name, description, system_prompt, summary_style, target_audience,
    max_items, language, is_active, is_preset,
    preview_title, preview_source, preview_body, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000004',
    '교육 트렌드 분석',
    '교육·HRD 관점으로 읽는 뉴스',
    '당신은 B2B 기업교육(HRD) 전문 애널리스트입니다. 모든 뉴스를 교육 산업 관점에서 해석합니다.

규칙:
- 교육/HRD 업계 용어(마이크로러닝, LMS, 역량 개발 등)를 자연스럽게 사용한다.
- 뉴스가 교육과 직접 관련이 없더라도, 기업교육 시장에 미칠 간접 영향을 분석한다.
- 구체적인 적용 아이디어를 포함한다.
- 원문을 그대로 인용하지 않는다.
- 각 줄은 50~70자 이내로 작성한다.

출력 형식:
📰 변화 포인트: (무엇이 바뀌고 있는가 — 2줄)
🎓 교육 시사점: (교육/HRD 업계에 어떤 의미인가 — 2줄)
💡 적용 아이디어: (교육 상품·서비스·콘텐츠에 어떻게 활용할 수 있는가 — 1줄)',
    '변화 포인트 2줄 + 교육 시사점 2줄 + 적용 아이디어 1줄',
    'HRD 담당자, 교육 기획자, 콘텐츠 개발자',
    5, 'ko', TRUE, TRUE,
    'AI 튜터링 기술, 대기업 신입사원 교육에 본격 도입',
    'Example Tech Daily · 1시간 전',
    '📰 변화 포인트: MegaCorp, ConglomerateCo 등 대기업이 신입사원 온보딩에 AI 튜터 시스템을 도입하기 시작했어요. 기존 집합교육 대비 학습 완료율이 35% 높고, 교육 담당자 업무량이 절반으로 줄었다는 시범 결과가 나왔습니다.

🎓 교육 시사점: AI 튜터링이 보조 도구에서 메인 교육 채널로 격상되는 신호예요. 특히 반복 학습, 실습 피드백 영역에서 기존 이러닝을 빠르게 대체할 수 있습니다.

💡 적용 아이디어: 기존 온보딩 콘텐츠를 AI 튜터 대화형으로 재설계하면, 고객사에 차별화된 교육 솔루션을 제안할 수 있어요.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM clipping_personas WHERE id = '00000000-0000-0000-0000-000000000004'
);

-- 1) 페르소나
UPDATE clipping_personas
SET name = '실무 요약 (팀 공유)',
    description = '팀 채널 공유용 핵심 요약',
    system_prompt = '당신은 실무 뉴스 에디터다. 핵심 사실 3개와 바로 실행할 액션 2개를 한국어로 간결하게 작성하라.',
    summary_style = '핵심 3줄 + 실행 포인트',
    target_audience = '팀원/실무 담당자',
    max_items = 5,
    language = 'ko',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000101';

INSERT INTO clipping_personas (
    id, name, description, system_prompt, summary_style, target_audience,
    max_items, language, is_active, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000101',
    '실무 요약 (팀 공유)',
    '팀 채널 공유용 핵심 요약',
    '당신은 실무 뉴스 에디터다. 핵심 사실 3개와 바로 실행할 액션 2개를 한국어로 간결하게 작성하라.',
    '핵심 3줄 + 실행 포인트',
    '팀원/실무 담당자',
    5,
    'ko',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM clipping_personas WHERE id = '00000000-0000-0000-0000-000000000101'
);

UPDATE clipping_personas
SET name = '경영진 브리핑 (의사결정)',
    description = '숫자/영향 중심 요약',
    system_prompt = '당신은 경영진 브리핑 작성자다. 1분 내 판단할 수 있도록 숫자, 비즈니스 영향, 리스크를 우선 정리하라.',
    summary_style = '지표/영향 중심 보고형',
    target_audience = '팀 리더/경영진',
    max_items = 4,
    language = 'ko',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000102';

INSERT INTO clipping_personas (
    id, name, description, system_prompt, summary_style, target_audience,
    max_items, language, is_active, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000102',
    '경영진 브리핑 (의사결정)',
    '숫자/영향 중심 요약',
    '당신은 경영진 브리핑 작성자다. 1분 내 판단할 수 있도록 숫자, 비즈니스 영향, 리스크를 우선 정리하라.',
    '지표/영향 중심 보고형',
    '팀 리더/경영진',
    4,
    'ko',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM clipping_personas WHERE id = '00000000-0000-0000-0000-000000000102'
);

UPDATE clipping_personas
SET name = '정책/규제 모니터링',
    description = '시행일/영향 대상 중심 요약',
    system_prompt = '당신은 규제 모니터링 담당자다. 변경사항, 시행일, 영향 대상, 준비 체크리스트를 우선 정리하라.',
    summary_style = '규정 변경/대응 체크리스트',
    target_audience = '컴플라이언스/운영 담당자',
    max_items = 5,
    language = 'ko',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000103';

INSERT INTO clipping_personas (
    id, name, description, system_prompt, summary_style, target_audience,
    max_items, language, is_active, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000103',
    '정책/규제 모니터링',
    '시행일/영향 대상 중심 요약',
    '당신은 규제 모니터링 담당자다. 변경사항, 시행일, 영향 대상, 준비 체크리스트를 우선 정리하라.',
    '규정 변경/대응 체크리스트',
    '컴플라이언스/운영 담당자',
    5,
    'ko',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM clipping_personas WHERE id = '00000000-0000-0000-0000-000000000103'
);

-- 2) 카테고리
UPDATE batch_categories
SET name = 'AI/테크',
    description = '생성형 AI, 플랫폼 업데이트, 개발 생산성 동향',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000101',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000201';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000201',
    'AI/테크',
    '생성형 AI, 플랫폼 업데이트, 개발 생산성 동향',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000101',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000201'
);

UPDATE batch_categories
SET name = 'HR/L&D',
    description = '인재개발, 조직문화, 교육 운영 트렌드',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000101',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000202';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000202',
    'HR/L&D',
    '인재개발, 조직문화, 교육 운영 트렌드',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000101',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000202'
);

UPDATE batch_categories
SET name = '투자/금융',
    description = '금리, 시장, 주요 기업 실적 및 투자 이슈',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000102',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000203';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000203',
    '투자/금융',
    '금리, 시장, 주요 기업 실적 및 투자 이슈',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000102',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000203'
);

UPDATE batch_categories
SET name = '정책/규제',
    description = 'AI, 데이터, 산업 정책/규제 변화 추적',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000103',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000204';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000204',
    '정책/규제',
    'AI, 데이터, 산업 정책/규제 변화 추적',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000103',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000204'
);

UPDATE batch_categories
SET name = '보안/인프라',
    description = '사이버보안, 클라우드 운영, 인프라 안정성',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000101',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000205';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000205',
    '보안/인프라',
    '사이버보안, 클라우드 운영, 인프라 안정성',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000101',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000205'
);

UPDATE batch_categories
SET name = '마케팅/이커머스',
    description = '퍼포먼스 마케팅, 전환, 이커머스 운영 이슈',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000102',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000206';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000206',
    '마케팅/이커머스',
    '퍼포먼스 마케팅, 전환, 이커머스 운영 이슈',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000102',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000206'
);

UPDATE batch_categories
SET name = '헬스케어/바이오',
    description = '바이오텍, 디지털 헬스, 의료 AI, 신약 파이프라인',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000102',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000207';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000207',
    '헬스케어/바이오',
    '바이오텍, 디지털 헬스, 의료 AI, 신약 파이프라인',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000102',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000207'
);

UPDATE batch_categories
SET name = '핀테크/결제',
    description = 'BNPL, 오픈뱅킹, 결제 인프라, 가상자산 규제',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000101',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000208';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000208',
    '핀테크/결제',
    'BNPL, 오픈뱅킹, 결제 인프라, 가상자산 규제',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000101',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000208'
);

-- 3) RSS 소스
UPDATE rss_sources
SET name = 'Google News - 생성형 AI',
    url = 'https://news.google.com/rss/search?q=생성형+AI+기술&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000201',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 85,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000301';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000301',
    'Google News - 생성형 AI',
    'https://news.google.com/rss/search?q=생성형+AI+기술&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000202',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    85,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000301'
);

UPDATE rss_sources
SET name = 'The Verge',
    url = 'https://www.theverge.com/rss/index.xml',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000201',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 82,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'GLOBAL',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000302';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000302',
    'The Verge',
    'https://www.theverge.com/rss/index.xml',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    82,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'GLOBAL'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000302'
);

UPDATE rss_sources
SET name = 'Google News - HR/L&D',
    url = 'https://news.google.com/rss/search?q=인재개발+교육&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000202',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 83,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000303';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000303',
    'Google News - HR/L&D',
    'https://news.google.com/rss/search?q=인재개발+교육&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000203',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    83,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000303'
);

UPDATE rss_sources
SET name = 'Google News - 투자/금융',
    url = 'https://news.google.com/rss/search?q=금리+시장+투자&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000203',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 84,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000305';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000305',
    'Google News - 투자/금융',
    'https://news.google.com/rss/search?q=금리+시장+투자&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    84,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000305'
);

UPDATE rss_sources
SET name = 'Google News - 정책/규제',
    url = 'https://news.google.com/rss/search?q=AI+규제+정책&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000204',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 86,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000307';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000307',
    'Google News - 정책/규제',
    'https://news.google.com/rss/search?q=AI+규제+정책&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000204',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    86,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000307'
);

UPDATE rss_sources
SET name = 'Google News - 보안/인프라',
    url = 'https://news.google.com/rss/search?q=사이버보안+침해사고&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000205',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 85,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000309';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000309',
    'Google News - 보안/인프라',
    'https://news.google.com/rss/search?q=사이버보안+침해사고&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000205',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    85,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000309'
);

UPDATE rss_sources
SET name = 'Google News - 마케팅/이커머스',
    url = 'https://news.google.com/rss/search?q=이커머스+마케팅+성과&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000206',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 83,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000311';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000311',
    'Google News - 마케팅/이커머스',
    'https://news.google.com/rss/search?q=이커머스+마케팅+성과&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000206',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    83,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000311'
);

UPDATE rss_sources
SET name = 'Google News - 헬스케어/바이오',
    url = 'https://news.google.com/rss/search?q=바이오텍+디지털헬스+의료AI&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000207',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 84,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000313';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000313',
    'Google News - 헬스케어/바이오',
    'https://news.google.com/rss/search?q=바이오텍+디지털헬스+의료AI&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000207',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    84,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000313'
);

UPDATE rss_sources
SET name = 'Google News - 핀테크/결제',
    url = 'https://news.google.com/rss/search?q=핀테크+결제+오픈뱅킹&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000208',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 83,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000315';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000315',
    'Google News - 핀테크/결제',
    'https://news.google.com/rss/search?q=핀테크+결제+오픈뱅킹&hl=ko&gl=KR&ceid=KR:ko',
    NULL,
    TRUE,
    '00000000-0000-0000-0000-000000000208',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    'seed-script',
    CURRENT_TIMESTAMP,
    'VERIFIED',
    83,
    'QUOTATION_ONLY',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    '로컬 검증용 시드 데이터',
    0,
    'DOMESTIC'
WHERE NOT EXISTS (
    SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000315'
);

-- 4) 최근 수집 기사
INSERT INTO rss_items (
    id, title, content, link, published_at, language, is_processed, category_id, rss_source_id, created_at
)
VALUES
(
    '00000000-0000-0000-0000-000000000401',
    'Gemini 2.5 기반 AI 에이전트 도입이 사내 개발 속도를 끌어올리고 있다',
    '여러 팀이 요약, 코드리뷰, QA 자동화를 하나의 워크플로로 묶기 시작했다. 운영팀은 비용보다 승인 프로세스와 로그 추적을 더 중요하게 보고 있다.',
    'https://local-seed.example.com/ai-agent-rollout',
    CURRENT_TIMESTAMP,
    'KOREAN',
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000301',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000402',
    'The Verge reports a sharper race around browser-native AI copilots',
    '브라우저 기반 copilot 기능이 검색, 문서 요약, 생산성 워크플로를 하나로 묶는 방향으로 경쟁하고 있다. 기업 고객은 보안 정책과 auditability를 우선 검토한다.',
    'https://local-seed.example.com/browser-ai-copilot',
    CURRENT_TIMESTAMP,
    'FOREIGN',
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000302',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000403',
    '기업 교육팀이 AI 러닝 코치와 마이크로 러닝을 결합해 교육 시간을 줄이고 있다',
    '교육 콘텐츠를 짧게 쪼개고, 개인별 코멘트를 AI가 붙이는 운영 모델이 늘고 있다. 다만 실제 수강 완료율과 현업 전이율을 같이 봐야 한다.',
    'https://local-seed.example.com/hr-learning-coach',
    CURRENT_TIMESTAMP,
    'KOREAN',
    TRUE,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000303',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000404',
    '금리 경로 불확실성이 커지면서 성장주와 현금흐름 우량주 선호가 동시에 나타난다',
    '시장에서는 실적 가시성과 AI 투자 지출 효과를 함께 보는 시각이 강해졌다. 투자위원회는 환율과 금리의 동시 변동성을 주요 리스크로 본다.',
    'https://local-seed.example.com/market-rotation-watch',
    CURRENT_TIMESTAMP,
    'KOREAN',
    TRUE,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000305',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000405',
    '주요 규제기관이 AI 결과물 표시 의무와 데이터 출처 설명 책임을 강화하는 초안을 검토 중이다',
    '정책 변화가 시행되면 B2B SaaS와 컨설팅형 도입 프로젝트 모두 설명가능성 요구를 먼저 맞춰야 한다. 운영 문서와 로그 보관 정책 정비가 선행 과제다.',
    'https://local-seed.example.com/regulation-traceability',
    CURRENT_TIMESTAMP,
    'KOREAN',
    TRUE,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000307',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000406',
    '침해사고 대응팀이 서드파티 SDK와 클라우드 권한 설계를 다시 점검하고 있다',
    '최근 사고 분석에서는 외부 연동 권한과 비밀값 저장소 구성이 반복적으로 등장했다. 플랫폼팀은 우선 회수 가능한 토큰 구조와 감사 로그 보강을 검토한다.',
    'https://local-seed.example.com/sdk-secret-hardening',
    CURRENT_TIMESTAMP,
    'KOREAN',
    TRUE,
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000309',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000407',
    'AI 기반 신약 후보 발굴이 임상 1상 단계로 진입하며 디지털 바이오 시대가 열리고 있다',
    '대형 제약사와 바이오벤처가 AI 신약 발굴 플랫폼을 통해 후보 물질을 선별하고, 일부는 임상 1상 승인을 받았다. 규제 당국은 AI 기반 임상 데이터 신뢰성 검증 기준 마련에 착수했다.',
    'https://local-seed.example.com/ai-drug-discovery',
    CURRENT_TIMESTAMP,
    'KOREAN',
    TRUE,
    '00000000-0000-0000-0000-000000000207',
    '00000000-0000-0000-0000-000000000313',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000408',
    '오픈뱅킹 2.0 시행으로 핀테크 결제 시장 경쟁이 격화되고 있다',
    '금융위원회의 오픈뱅킹 2.0 시행과 함께 핀테크 기업의 결제 수수료 인하 경쟁이 본격화되고 있다. BNPL 서비스 규제 가이드라인도 확정되면서 시장 재편이 예상된다.',
    'https://local-seed.example.com/openbanking-fintech',
    CURRENT_TIMESTAMP,
    'KOREAN',
    TRUE,
    '00000000-0000-0000-0000-000000000208',
    '00000000-0000-0000-0000-000000000315',
    CURRENT_TIMESTAMP
);

-- 5) 요약/검토 데이터
INSERT INTO batch_summaries (
    id, original_title, translated_title, summary, keywords, insights, source_link,
    is_sent_to_slack, category_id, rss_item_id, created_at, importance_score
)
VALUES
(
    '00000000-0000-0000-0000-000000000501',
    'Gemini 2.5 기반 AI 에이전트 도입이 사내 개발 속도를 끌어올리고 있다',
    NULL,
    '여러 팀이 에이전트형 워크플로를 붙이면서 개발/QA 반복 시간이 줄고 있다. 다만 실제 운영 가치는 모델 성능보다 승인 흐름과 로그 추적 구조를 먼저 정리했을 때 높아진다.',
    '["AI 에이전트","Gemini 2.5","개발 생산성"]',
    '도입 우선순위는 기능 추가보다 승인·감사 설계다.',
    'https://local-seed.example.com/ai-agent-rollout',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000401',
    CURRENT_TIMESTAMP,
    0.92
),
(
    '00000000-0000-0000-0000-000000000502',
    'The Verge reports a sharper race around browser-native AI copilots',
    '브라우저 AI copilot 경쟁이 빨라지고 있다',
    '브라우저 안에서 검색, 요약, 실행을 묶는 제품 경쟁이 본격화되고 있다. 기업 도입에서는 편의성보다 정책 제어와 감사 추적 가능성이 실제 구매 포인트가 된다.',
    '["브라우저 AI","Copilot","정책 제어"]',
    '브라우저 레벨 통합은 확장성보다 보안 정책 충돌 여부를 먼저 본다.',
    'https://local-seed.example.com/browser-ai-copilot',
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000402',
    CURRENT_TIMESTAMP,
    0.81
),
(
    '00000000-0000-0000-0000-000000000503',
    '기업 교육팀이 AI 러닝 코치와 마이크로 러닝을 결합해 교육 시간을 줄이고 있다',
    NULL,
    '교육팀은 짧은 콘텐츠와 AI 피드백을 묶어 학습 완료율을 높이려 하고 있다. 그러나 수강자 만족도만 보면 착시가 생길 수 있어, 현업 전이율과 리더 피드백을 함께 추적해야 한다.',
    '["L&D","마이크로러닝","학습 전이"]',
    '성과 지표를 수강 완료율 하나로 두지 않는 운영 설계가 핵심이다.',
    'https://local-seed.example.com/hr-learning-coach',
    FALSE,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000403',
    CURRENT_TIMESTAMP,
    0.74
),
(
    '00000000-0000-0000-0000-000000000504',
    '금리 경로 불확실성이 커지면서 성장주와 현금흐름 우량주 선호가 동시에 나타난다',
    NULL,
    '시장에서는 성장성과 현금흐름 안정성을 동시에 보는 혼합 선호가 강화되고 있다. 투자 의사결정에서는 금리 변화보다 환율과 AI 투자 집행 속도를 함께 보려는 흐름이 나타난다.',
    '["금리","실적","환율"]',
    '단기 시황보다 포트폴리오 리스크 설명 문구를 먼저 정리할 필요가 있다.',
    'https://local-seed.example.com/market-rotation-watch',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000404',
    CURRENT_TIMESTAMP,
    0.67
),
(
    '00000000-0000-0000-0000-000000000505',
    '주요 규제기관이 AI 결과물 표시 의무와 데이터 출처 설명 책임을 강화하는 초안을 검토 중이다',
    NULL,
    '규제 초안은 AI 결과물 표시, 데이터 출처 설명, 로그 보관 책임을 더 구체적으로 요구하는 방향이다. SaaS 운영팀은 기능 출시보다 로그 정책과 문서 증적 체계를 먼저 정비해야 한다.',
    '["AI 규제","출처 설명","감사 로그"]',
    '정책 대응은 기능 수정이 아니라 운영 문서/로그 체계 정비가 중심이다.',
    'https://local-seed.example.com/regulation-traceability',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000405',
    CURRENT_TIMESTAMP,
    0.89
),
(
    '00000000-0000-0000-0000-000000000506',
    '침해사고 대응팀이 서드파티 SDK와 클라우드 권한 설계를 다시 점검하고 있다',
    NULL,
    '최근 대응 사례는 외부 SDK 권한과 비밀값 관리가 보안 사고의 반복 원인임을 보여준다. 플랫폼팀은 토큰 회수 전략과 감사 로그 보강을 우선순위로 재정렬하고 있다.',
    '["보안","시크릿 관리","권한 설계"]',
    '보안 투자 포인트가 도구 추가보다 권한/비밀값 운영으로 이동하고 있다.',
    'https://local-seed.example.com/sdk-secret-hardening',
    TRUE,
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000406',
    CURRENT_TIMESTAMP,
    0.86
),
(
    '00000000-0000-0000-0000-000000000507',
    'AI 기반 신약 후보 발굴이 임상 1상 단계로 진입하며 디지털 바이오 시대가 열리고 있다',
    NULL,
    'AI 신약 발굴 플랫폼이 후보 물질 선별에서 실제 임상 진입으로 이어지고 있다. 규제 당국이 AI 기반 임상 데이터 신뢰성 기준을 마련 중이어서, 바이오 기업은 데이터 거버넌스를 선제 정비해야 한다.',
    '["AI 신약","디지털 바이오","임상 1상","데이터 거버넌스"]',
    'AI 신약 발굴이 실험실에서 규제 검증 단계로 이동하고 있다.',
    'https://local-seed.example.com/ai-drug-discovery',
    FALSE,
    '00000000-0000-0000-0000-000000000207',
    '00000000-0000-0000-0000-000000000407',
    CURRENT_TIMESTAMP,
    0.88
),
(
    '00000000-0000-0000-0000-000000000508',
    '오픈뱅킹 2.0 시행으로 핀테크 결제 시장 경쟁이 격화되고 있다',
    NULL,
    '오픈뱅킹 2.0이 시행되면서 핀테크 결제 수수료 경쟁이 심화되고 있다. BNPL 규제 가이드라인 확정으로 소비자 보호 기준이 높아졌으며, 소규모 핀테크는 차별화 전략이 시급하다.',
    '["오픈뱅킹","핀테크","BNPL","결제 수수료"]',
    '결제 인프라 개방이 수수료 인하를 넘어 서비스 품질 경쟁으로 확대되고 있다.',
    'https://local-seed.example.com/openbanking-fintech',
    FALSE,
    '00000000-0000-0000-0000-000000000208',
    '00000000-0000-0000-0000-000000000408',
    CURRENT_TIMESTAMP,
    0.79
);

INSERT INTO clipping_review_items (
    summary_id, category_id, status, reason, reviewed_by, reviewed_at, created_at, updated_at
)
VALUES
(
    '00000000-0000-0000-0000-000000000501',
    '00000000-0000-0000-0000-000000000201',
    'REVIEW',
    '자동 분류는 높지만 실제 팀 공지 필요 여부를 마지막으로 확인',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000502',
    '00000000-0000-0000-0000-000000000201',
    'INCLUDE',
    '브라우저 제품 경쟁 동향으로 발송 가치가 높음',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000503',
    '00000000-0000-0000-0000-000000000202',
    'REVIEW',
    '교육팀 도입 사례라 맥락 확인 후 공유',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000504',
    '00000000-0000-0000-0000-000000000203',
    'EXCLUDE',
    '시장 코멘트성 기사로 중복 가능성이 높음',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000505',
    '00000000-0000-0000-0000-000000000204',
    'REVIEW',
    '정책 시행일 확정 여부 확인 필요',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000506',
    '00000000-0000-0000-0000-000000000205',
    'INCLUDE',
    '보안 운영 체크리스트로 바로 활용 가능',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO clipping_review_item_audits (
    id, summary_id, category_id, from_status, to_status, reason, reviewed_by, reviewed_at, created_at
)
VALUES
(
    '00000000-0000-0000-0000-000000000601',
    '00000000-0000-0000-0000-000000000501',
    '00000000-0000-0000-0000-000000000201',
    NULL,
    'REVIEW',
    '로컬 검토 대기 예시',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000602',
    '00000000-0000-0000-0000-000000000502',
    '00000000-0000-0000-0000-000000000201',
    NULL,
    'INCLUDE',
    '발송 가치 높음',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000603',
    '00000000-0000-0000-0000-000000000503',
    '00000000-0000-0000-0000-000000000202',
    NULL,
    'REVIEW',
    '운영 공유 여부 재확인',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000604',
    '00000000-0000-0000-0000-000000000504',
    '00000000-0000-0000-0000-000000000203',
    NULL,
    'EXCLUDE',
    '시장 코멘트성으로 제외',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000605',
    '00000000-0000-0000-0000-000000000505',
    '00000000-0000-0000-0000-000000000204',
    NULL,
    'REVIEW',
    '정책 시행일 확인 필요',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000606',
    '00000000-0000-0000-0000-000000000506',
    '00000000-0000-0000-0000-000000000205',
    NULL,
    'INCLUDE',
    '즉시 공유 가능한 보안 체크리스트',
    'local-seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 6) 월간 통계
INSERT INTO clipping_stats (
    id, category_id, stat_date, items_collected, items_summarized, items_sent,
    top_keywords, avg_importance_score, created_at, items_duplicates, slack_send_attempts, slack_send_successes
)
VALUES
(
    '00000000-0000-0000-0000-000000000801',
    '00000000-0000-0000-0000-000000000201',
    CURRENT_DATE - 2,
    12, 8, 4,
    '["AI 에이전트","Gemini","자동화"]',
    0.79,
    CURRENT_TIMESTAMP,
    2, 4, 4
),
(
    '00000000-0000-0000-0000-000000000802',
    '00000000-0000-0000-0000-000000000201',
    CURRENT_DATE - 1,
    14, 9, 5,
    '["Copilot","브라우저 AI","정책 제어"]',
    0.83,
    CURRENT_TIMESTAMP,
    3, 5, 5
),
(
    '00000000-0000-0000-0000-000000000803',
    '00000000-0000-0000-0000-000000000201',
    CURRENT_DATE,
    16, 10, 5,
    '["생성형 AI","승인 흐름","로그"]',
    0.87,
    CURRENT_TIMESTAMP,
    2, 5, 4
),
(
    '00000000-0000-0000-0000-000000000804',
    '00000000-0000-0000-0000-000000000202',
    CURRENT_DATE - 2,
    7, 4, 2,
    '["L&D","마이크로러닝","교육 운영"]',
    0.66,
    CURRENT_TIMESTAMP,
    1, 2, 2
),
(
    '00000000-0000-0000-0000-000000000805',
    '00000000-0000-0000-0000-000000000202',
    CURRENT_DATE - 1,
    8, 5, 2,
    '["교육 분석","현업 전이","코칭"]',
    0.7,
    CURRENT_TIMESTAMP,
    1, 2, 2
),
(
    '00000000-0000-0000-0000-000000000806',
    '00000000-0000-0000-0000-000000000202',
    CURRENT_DATE,
    9, 5, 3,
    '["러닝 코치","개인화","수강 완료율"]',
    0.72,
    CURRENT_TIMESTAMP,
    1, 3, 3
),
(
    '00000000-0000-0000-0000-000000000807',
    '00000000-0000-0000-0000-000000000203',
    CURRENT_DATE - 2,
    11, 6, 3,
    '["금리","실적","시장 변동"]',
    0.62,
    CURRENT_TIMESTAMP,
    2, 3, 3
),
(
    '00000000-0000-0000-0000-000000000808',
    '00000000-0000-0000-0000-000000000203',
    CURRENT_DATE - 1,
    10, 6, 3,
    '["포트폴리오","환율","리스크"]',
    0.64,
    CURRENT_TIMESTAMP,
    2, 3, 2
),
(
    '00000000-0000-0000-0000-000000000809',
    '00000000-0000-0000-0000-000000000203',
    CURRENT_DATE,
    13, 7, 4,
    '["투자위원회","현금흐름","성장주"]',
    0.69,
    CURRENT_TIMESTAMP,
    2, 4, 4
)
ON CONFLICT DO NOTHING;

-- 7) 피드백
-- V117 에서 fk_summary_feedback_user FK (summary_feedback.user_id → admin_users.id, ON DELETE CASCADE)
-- 가 추가되어 기존 'slack:UDEVxxx' 값은 orphan 으로 insert 불가 — 시드 로컬
-- 고정 계정 UUID (dev.user 계열) 으로 치환한다. 동일 id 재실행 시 no-op.
INSERT INTO summary_feedback (id, summary_id, feedback_type, user_id, created_at)
VALUES
('00000000-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000501', 'LIKE',    '00000000-0000-0000-0000-000000000002', CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000902', '00000000-0000-0000-0000-000000000502', 'LIKE',    '00000000-0000-0000-0000-000000000003', CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000903', '00000000-0000-0000-0000-000000000505', 'DISLIKE', '00000000-0000-0000-0000-000000000004', CURRENT_TIMESTAMP),
('00000000-0000-0000-0000-000000000904', '00000000-0000-0000-0000-000000000506', 'NEUTRAL', '00000000-0000-0000-0000-000000000005', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 8) 사용자 요청
INSERT INTO clipping_user_requests (
    id, requester_user_id, request_name, source_name, source_url, slack_channel_id, persona_name,
    persona_prompt, summary_style, target_audience, request_note, status, review_note, reviewed_by_user_id,
    reviewed_at, approved_category_id, approved_persona_id, approved_source_id, created_at, updated_at
)
VALUES
(
    '00000000-0000-0000-0000-000000001001',
    '00000000-0000-0000-0000-000000000003',
    '국내 AI 정책 브리핑',
    'KISDI 정책 브리핑',
    'https://news.google.com/rss/search?q=AI+규제+정책&hl=ko&gl=KR&ceid=KR:ko',
    'C0123456789',
    '정책 브리핑',
    '정책 변화, 시행일, 영향 부서를 한국어로 짧게 정리해줘.',
    '정책 변경 중심',
    '컴플라이언스 리드',
    'AI 규제/공시 흐름을 한 채널로 보고 싶습니다.',
    'PENDING',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000001002',
    '00000000-0000-0000-0000-000000000002',
    '보안 사고 대응 모니터링',
    'CISA Advisories',
    'https://www.cisa.gov/cybersecurity-advisories/all.xml',
    'C0123456789',
    '보안 브리핑',
    '보안 사고 대응 관점에서 긴급도, 조치 항목, 패치 우선순위를 정리해줘.',
    '사고 대응 체크리스트',
    '보안 운영팀',
    '운영팀 아침 브리핑 용도입니다.',
    'APPROVED',
    '기존 보안 카테고리에 연결해 사용하기 완료',
    '00000000-0000-0000-0000-000000000001',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000309',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000001003',
    '00000000-0000-0000-0000-000000000004',
    '브랜드 마케팅 경쟁사 모니터링',
    'Search Engine Land',
    'https://searchengineland.com/feed',
    'C0123456789',
    '마케팅 실무 요약',
    '브랜드와 퍼포먼스 지표 변화가 있으면 실무 액션 중심으로 요약해줘.',
    '전환/캠페인 관점',
    '퍼포먼스 마케터',
    '경쟁사 퍼널 변화와 광고 상품 업데이트를 보고 싶습니다.',
    'REJECTED',
    '동일한 요청이 이미 운영 중인 카테고리와 중복되어 반려',
    '00000000-0000-0000-0000-000000000001',
    CURRENT_TIMESTAMP,
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
-- dev.user 승인 구독은 정책 한도(최대 5개)를 넘지 않도록 5건으로 유지한다.
(
    '00000000-0000-0000-0000-000000001004',
    '00000000-0000-0000-0000-000000000002',
    'AI/테크 뉴스',
    'Google News - 생성형 AI',
    'https://news.google.com/rss/search?q=generative+ai',
    'C0123456789',
    '실무 요약', '핵심 사실 3개를 한국어로 요약하라.',
    NULL, NULL, NULL,
    'APPROVED',
    '카테고리 연결 완료',
    '00000000-0000-0000-0000-000000000001',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000301',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000001005',
    '00000000-0000-0000-0000-000000000002',
    '정책/규제 동향',
    'Google News - 정책/규제',
    'https://news.google.com/rss/search?q=regulation+policy',
    'C0123456789',
    '정책 모니터링', '정책 변화와 시행일을 정리하라.',
    NULL, NULL, NULL,
    'APPROVED',
    '카테고리 연결 완료',
    '00000000-0000-0000-0000-000000000001',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000103',
    '00000000-0000-0000-0000-000000000307',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000001006',
    '00000000-0000-0000-0000-000000000002',
    'HR/L&D 동향',
    'Google News - HR/L&D',
    'https://news.google.com/rss/search?q=learning+development',
    'C0123456789',
    '실무 요약', '요약하라.',
    NULL, NULL, NULL,
    'APPROVED',
    '카테고리 연결 완료',
    '00000000-0000-0000-0000-000000000001',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000303',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000001007',
    '00000000-0000-0000-0000-000000000002',
    '투자/금융 뉴스',
    'Google News - 투자/금융',
    'https://news.google.com/rss/search?q=financial+markets',
    'C0123456789',
    '경영진 브리핑', '요약하라.',
    NULL, NULL, NULL,
    'APPROVED',
    '카테고리 연결 완료',
    '00000000-0000-0000-0000-000000000001',
    CURRENT_TIMESTAMP,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000102',
    '00000000-0000-0000-0000-000000000305',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- ============================================================
-- News Intelligence 시드 데이터
-- ============================================================

-- 1) 기존 batch_summaries에 날짜를 1달(30일)로 분산 + sentiment/event_type 추가
UPDATE batch_summaries SET created_at = CURRENT_TIMESTAMP - INTERVAL '28' DAY, sentiment = 'POSITIVE', event_type = 'PRODUCT_LAUNCH' WHERE id = '00000000-0000-0000-0000-000000000501';
UPDATE batch_summaries SET created_at = CURRENT_TIMESTAMP - INTERVAL '25' DAY, sentiment = 'NEUTRAL', event_type = 'PARTNERSHIP' WHERE id = '00000000-0000-0000-0000-000000000502';
UPDATE batch_summaries SET created_at = CURRENT_TIMESTAMP - INTERVAL '22' DAY, sentiment = 'POSITIVE', event_type = 'OTHER' WHERE id = '00000000-0000-0000-0000-000000000503';
UPDATE batch_summaries SET created_at = CURRENT_TIMESTAMP - INTERVAL '19' DAY, sentiment = 'NEGATIVE', event_type = 'POLICY' WHERE id = '00000000-0000-0000-0000-000000000504';
UPDATE batch_summaries SET created_at = CURRENT_TIMESTAMP - INTERVAL '16' DAY, sentiment = 'POSITIVE', event_type = 'FUNDING' WHERE id = '00000000-0000-0000-0000-000000000505';
UPDATE batch_summaries SET created_at = CURRENT_TIMESTAMP - INTERVAL '13' DAY, sentiment = 'NEUTRAL', event_type = 'PERSONNEL' WHERE id = '00000000-0000-0000-0000-000000000506';

-- 2) 추가 batch_summaries (511-522): 다양한 날짜/키워드/감성/이벤트 유형
DELETE FROM batch_summaries
WHERE id IN (
    '00000000-0000-0000-0000-000000000511',
    '00000000-0000-0000-0000-000000000512',
    '00000000-0000-0000-0000-000000000513',
    '00000000-0000-0000-0000-000000000514',
    '00000000-0000-0000-0000-000000000515',
    '00000000-0000-0000-0000-000000000516',
    '00000000-0000-0000-0000-000000000517',
    '00000000-0000-0000-0000-000000000518',
    '00000000-0000-0000-0000-000000000519',
    '00000000-0000-0000-0000-000000000520',
    '00000000-0000-0000-0000-000000000521',
    '00000000-0000-0000-0000-000000000522',
    '00000000-0000-0000-0000-000000000531',
    '00000000-0000-0000-0000-000000000532',
    '00000000-0000-0000-0000-000000000533',
    '00000000-0000-0000-0000-000000000534',
    '00000000-0000-0000-0000-000000000535',
    '00000000-0000-0000-0000-000000000536',
    '00000000-0000-0000-0000-000000000537',
    '00000000-0000-0000-0000-000000000538',
    '00000000-0000-0000-0000-000000000539',
    '00000000-0000-0000-0000-000000000540',
    '00000000-0000-0000-0000-000000000541',
    '00000000-0000-0000-0000-000000000542',
    '00000000-0000-0000-0000-000000000543',
    '00000000-0000-0000-0000-000000000544',
    '00000000-0000-0000-0000-000000000545',
    '00000000-0000-0000-0000-000000000546',
    '00000000-0000-0000-0000-000000000547',
    '00000000-0000-0000-0000-000000000548',
    '00000000-0000-0000-0000-000000000549',
    '00000000-0000-0000-0000-000000000550'
);

INSERT INTO batch_summaries (
    id, original_title, translated_title, summary, keywords, insights, source_link,
    is_sent_to_slack, category_id, rss_item_id, created_at, importance_score,
    sentiment, event_type
)
VALUES
(
    '00000000-0000-0000-0000-000000000511',
    'AlphaEd가 기업 맞춤 AI 리스킬링 프로그램을 전면 개편했다',
    NULL,
    'AlphaEd는 기업 고객 대상 AI 리스킬링 과정을 전면 개편하고, 실무 프로젝트 기반 커리큘럼을 강화했다. 비개발 직군까지 커버하는 것이 차별점이며, 수료율 기반 성과 보장 모델을 도입했다.',
    '["AlphaEd","리스킬링","AI교육","기업교육"]',
    '경쟁사 리스킬링 상품 확대는 우리 B2B 제안서에 차별화 포인트를 재정비할 시점이다.',
    'https://local-seed.example.com/alphaed-reskilling',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000401',
    CURRENT_TIMESTAMP - INTERVAL '29' DAY,
    0.88,
    'NEUTRAL', 'PRODUCT_LAUNCH'
),
(
    '00000000-0000-0000-0000-000000000512',
    'BetaCampus, MegaCorp 그룹사 대상 디지털전환 교육 계약 체결',
    NULL,
    'BetaCampus가 MegaCorp 계열사 5곳과 디지털전환 역량 강화 교육 계약을 체결했다. 연간 수만 명 규모의 교육 물량으로, LMS 플랫폼 고도화와 AI 튜터 기능이 포함된다.',
    '["BetaCampus","디지털전환","MegaCorp","LMS"]',
    '대기업 장기 계약은 플랫폼 종속을 만들기 때문에 중소기업 시장 공략이 우리의 기회다.',
    'https://local-seed.example.com/betacampus-testcorp-deal',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000402',
    CURRENT_TIMESTAMP - INTERVAL '27' DAY,
    0.91,
    'POSITIVE', 'PARTNERSHIP'
),
(
    '00000000-0000-0000-0000-000000000513',
    'GammaLearn, 월간 활성 사용자 200만 돌파… 커뮤니티 기반 성장 가속',
    NULL,
    'GammaLearn이 MAU 200만을 돌파하며 국내 최대 개발자 학습 플랫폼으로 자리매김했다. 커뮤니티 Q&A와 멘토링 기능이 리텐션을 높이는 핵심 요인으로 분석된다.',
    '["GammaLearn","에듀테크","커뮤니티","개발자교육"]',
    '커뮤니티 기반 리텐션은 B2C에서 효과적이나, B2B에는 다른 접근이 필요하다.',
    'https://local-seed.example.com/gammalearn-mau-200m',
    TRUE,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000403',
    CURRENT_TIMESTAMP - INTERVAL '25' DAY,
    0.79,
    'POSITIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000514',
    'DeltaClass이 글로벌 시장 철수 후 국내 기업교육으로 피벗한다',
    NULL,
    'DeltaClass이 일본·미국 시장 철수를 공식화하고, 국내 기업 교육 시장 진출을 선언했다. 크리에이터 기반 콘텐츠를 기업 연수용으로 재편하는 전략이다.',
    '["DeltaClass","기업교육","피벗","글로벌"]',
    'DeltaClass의 기업교육 진출은 크리에이터 콘텐츠 품질 관리가 관건이 될 것이다.',
    'https://local-seed.example.com/deltaclass-pivot-b2b',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000404',
    CURRENT_TIMESTAMP - INTERVAL '23' DAY,
    0.85,
    'NEGATIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000515',
    '고용부, 2026 법정의무교육 온라인 이수 기준 강화 고시안 발표',
    NULL,
    '고용노동부가 법정의무교육의 온라인 이수 요건을 강화하는 고시안을 발표했다. 학습 시간 인증과 평가 연동이 필수가 되면서, LMS 업체들의 기능 업데이트가 불가피하다.',
    '["법정교육","고용부","온라인교육","HRD"]',
    '규제 변경은 기존 LMS 고객의 업그레이드 수요로 직결된다.',
    'https://local-seed.example.com/legal-education-2026',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000405',
    CURRENT_TIMESTAMP - INTERVAL '21' DAY,
    0.93,
    'NEUTRAL', 'POLICY'
),
(
    '00000000-0000-0000-0000-000000000516',
    '마이크로러닝 플랫폼 시장, 2026년 전년 대비 35% 성장 전망',
    NULL,
    '글로벌 마이크로러닝 시장이 2026년 35% 성장할 것으로 전망된다. 모바일 우선 학습 경험과 AI 기반 개인화가 핵심 성장 동력이다. 국내에서도 기업교육 시장에서 마이크로러닝 도입이 가속화되고 있다.',
    '["마이크로러닝","에듀테크","모바일학습","AI교육"]',
    '마이크로러닝은 콘텐츠 제작 효율이 핵심이므로 AI 자동화 도구 투자가 중요하다.',
    'https://local-seed.example.com/microlearning-market-2026',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000401',
    CURRENT_TIMESTAMP - INTERVAL '19' DAY,
    0.76,
    'POSITIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000517',
    'AlphaEd, 시리즈C 500억 투자 유치… 기업 교육 플랫폼 확장',
    NULL,
    'AlphaEd Holdings(AlphaEd 모회사)가 시리즈C에서 500억원을 유치했다. 기업 교육 SaaS 고도화와 해외 진출 자금으로 사용될 예정이며, 기업 가치 3000억원 이상으로 평가됐다.',
    '["AlphaEd","투자유치","AlphaEd Holdings","에듀테크"]',
    '경쟁사 자금 확보는 마케팅 공세와 인재 확보 경쟁 심화를 의미한다.',
    'https://local-seed.example.com/alphaed-series-c',
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000402',
    CURRENT_TIMESTAMP - INTERVAL '17' DAY,
    0.95,
    'POSITIVE', 'FUNDING'
),
(
    '00000000-0000-0000-0000-000000000518',
    '업스킬링 트렌드: 비개발 직군의 코딩교육 수요가 폭발적으로 증가',
    NULL,
    '마케터, 기획자, HR 담당자 등 비개발 직군의 코딩 교육 수요가 전년 대비 120% 증가했다. 노코드/로우코드 도구 활용과 데이터 리터러시가 주요 학습 목표로 나타났다.',
    '["업스킬링","코딩교육","노코드","데이터리터러시"]',
    '비개발 직군 교육은 실습 환경 제공이 핵심 차별화 요소다.',
    'https://local-seed.example.com/upskilling-non-dev',
    FALSE,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000403',
    CURRENT_TIMESTAMP - INTERVAL '14' DAY,
    0.72,
    'POSITIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000519',
    '원격학습 피로감 심화… 기업 교육 이탈률 20% 상승',
    NULL,
    '코로나 이후 원격 교육 피로감이 누적되면서 기업 교육 이탈률이 20% 상승했다. 하이브리드 교육 모델과 게이미피케이션 도입이 대안으로 떠오르고 있다.',
    '["원격학습","교육이탈","하이브리드","게이미피케이션"]',
    '이탈률 상승은 교육 형식 다양화와 참여형 콘텐츠 투자의 근거가 된다.',
    'https://local-seed.example.com/remote-learning-fatigue',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000404',
    CURRENT_TIMESTAMP - INTERVAL '11' DAY,
    0.68,
    'NEGATIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000520',
    'BetaCampus, AI 기반 학습 경로 추천 엔진 베타 출시',
    NULL,
    'BetaCampus가 AI 기반 학습 경로 추천 엔진을 베타 출시했다. 직무별 역량 갭 분석과 개인화된 커리큘럼 추천이 핵심 기능이며, 올 상반기 정식 출시를 목표로 한다.',
    '["BetaCampus","AI교육","학습추천","개인화"]',
    'AI 기반 추천은 학습 데이터 축적이 핵심이므로 기존 고객 기반이 유리하다.',
    'https://local-seed.example.com/betacampus-ai-recommend',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000405',
    CURRENT_TIMESTAMP - INTERVAL '8' DAY,
    0.83,
    'POSITIVE', 'PRODUCT_LAUNCH'
),
(
    '00000000-0000-0000-0000-000000000521',
    'GammaLearn, 기업 전용 요금제 출시로 B2B 시장 본격 진출',
    NULL,
    'GammaLearn이 기업 전용 요금제를 출시하며 B2B 시장에 본격 진출했다. 팀 단위 학습 관리와 수료증 발급 기능이 포함되며, 100개 이상 기업이 베타 테스트에 참여했다.',
    '["GammaLearn","기업교육","B2B","LMS"]',
    'GammaLearn의 B2B 진출은 개발자 교육 영역에서의 직접 경쟁 심화를 의미한다.',
    'https://local-seed.example.com/gammalearn-b2b-launch',
    TRUE,
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000406',
    CURRENT_TIMESTAMP - INTERVAL '5' DAY,
    0.87,
    'NEUTRAL', 'PRODUCT_LAUNCH'
),
(
    '00000000-0000-0000-0000-000000000522',
    'HRD 담당자 설문: 2026 기업교육 투자 우선순위 1위는 AI 역량',
    NULL,
    '500명의 HRD 담당자를 대상으로 한 설문에서 2026년 교육 투자 우선순위 1위로 AI 역량이 선정됐다. 리더십, 데이터 분석, 법정교육이 뒤를 이었으며, 교육 예산은 전년 대비 15% 증가 전망이다.',
    '["HRD","기업교육","AI역량","교육예산"]',
    'AI 역량 교육 수요 증가는 콘텐츠 차별화와 ROI 증명이 핵심 경쟁력이 된다.',
    'https://local-seed.example.com/hrd-survey-2026',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000401',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY,
    0.90,
    'POSITIVE', 'OTHER'
);

-- 2-b) batch_summaries: 경쟁사 타임라인용 추가 시드 데이터
INSERT INTO batch_summaries (
    id, original_title, translated_title, summary, keywords, insights, source_link,
    is_sent_to_slack, category_id, rss_item_id, created_at, importance_score,
    sentiment, event_type
)
VALUES
(
    '00000000-0000-0000-0000-000000000531',
    'Udemy Business, 아시아 태평양 시장 공략 위해 한국 지사 설립 검토',
    NULL,
    'Udemy가 아태 시장 확장의 일환으로 한국 지사 설립을 검토하고 있다. 한국어 콘텐츠 로컬라이징과 국내 기업 파트너십을 통해 B2B 시장 진출을 본격화할 계획이다.',
    '["Udemy","한국진출","B2B","에듀테크"]',
    'Udemy의 국내 직접 진출은 가격 경쟁과 글로벌 콘텐츠 접근성에서 위협이 될 수 있다.',
    'https://local-seed.example.com/udemy-korea-office',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000401',
    CURRENT_TIMESTAMP - INTERVAL '30' DAY,
    0.91,
    'NEUTRAL', 'PARTNERSHIP'
),
(
    '00000000-0000-0000-0000-000000000532',
    'AlphaEd, SearchCo Cloud와 AI 교육 콘텐츠 공동 개발 MOU 체결',
    NULL,
    'AlphaEd가 SearchCo Cloud와 AI 실무 교육 콘텐츠 공동 개발을 위한 MOU를 체결했다. CustomLLM 기반 실습 환경을 제공하며, 기업 고객 대상 AI 전환 교육을 강화한다.',
    '["AlphaEd","SearchCo Cloud","AI교육","MOU"]',
    '클라우드 벤더와의 제휴는 실습 인프라 차별화로 이어지므로 주목할 필요가 있다.',
    'https://local-seed.example.com/alphaed-searchco-mou',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000402',
    CURRENT_TIMESTAMP - INTERVAL '28' DAY,
    0.87,
    'POSITIVE', 'PARTNERSHIP'
),
(
    '00000000-0000-0000-0000-000000000533',
    'GammaLearn 개인정보 유출 사고 발생… 약 1만 명 이메일 노출',
    NULL,
    'GammaLearn에서 시스템 업데이트 과정 중 약 1만 명의 이용자 이메일이 외부에 노출되는 사고가 발생했다. GammaLearn Labs 측은 즉시 조치를 취하고 Data Protection Authority에 신고했다고 밝혔다.',
    '["GammaLearn","개인정보","보안사고","GammaLearn Labs"]',
    '경쟁사 보안 사고는 우리 플랫폼의 보안 체계를 점검하고 차별화 포인트로 활용할 기회다.',
    'https://local-seed.example.com/gammalearn-data-breach',
    TRUE,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000403',
    CURRENT_TIMESTAMP - INTERVAL '26' DAY,
    0.93,
    'NEGATIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000534',
    'DeltaClass, CTO 영입으로 기술 조직 재편… AI 추천 시스템 강화 예고',
    NULL,
    'DeltaClass이 전 MessengerCo AI랩 출신 CTO를 영입하며 기술 조직을 재편했다. AI 기반 학습 추천 시스템과 콘텐츠 자동 생성 도구 개발에 집중할 계획이다.',
    '["DeltaClass","CTO영입","AI추천","인사"]',
    '핵심 인재 영입은 6개월 내 제품 방향성 변화로 이어지므로 동향을 추적해야 한다.',
    'https://local-seed.example.com/deltaclass-new-cto',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000404',
    CURRENT_TIMESTAMP - INTERVAL '24' DAY,
    0.82,
    'NEUTRAL', 'PERSONNEL'
),
(
    '00000000-0000-0000-0000-000000000535',
    '유데미, 2025 글로벌 스킬 인사이트 보고서 발표… 생성형 AI 스킬 수요 4배 증가',
    NULL,
    'Udemy가 2025 글로벌 스킬 인사이트 보고서를 발표했다. 생성형 AI 관련 강좌 수강이 전년 대비 4배 증가했으며, 프롬프트 엔지니어링과 AI 에이전트 개발이 최고 인기 주제로 나타났다.',
    '["Udemy","스킬보고서","생성형AI","프롬프트엔지니어링"]',
    '글로벌 스킬 트렌드 데이터는 우리 커리큘럼 기획의 참고 자료로 활용 가능하다.',
    'https://local-seed.example.com/udemy-skill-insight-2025',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000405',
    CURRENT_TIMESTAMP - INTERVAL '22' DAY,
    0.78,
    'POSITIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000536',
    'BetaCampus, 고용노동부 K-디지털 트레이닝 사업 최다 선정',
    NULL,
    'BetaCampus가 고용노동부 K-디지털 트레이닝 사업에서 12개 과정으로 최다 선정됐다. AI, 클라우드, 데이터 분석 등 디지털 핵심 역량 과정이 포함되며, 정부 교육 예산 수주 경쟁에서 우위를 차지했다.',
    '["BetaCampus","K디지털","고용부","정부사업"]',
    '정부 사업 수주는 안정적 매출원이자 브랜드 신뢰도 강화 효과가 있다.',
    'https://local-seed.example.com/betacampus-k-digital',
    FALSE,
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000406',
    CURRENT_TIMESTAMP - INTERVAL '20' DAY,
    0.85,
    'POSITIVE', 'PARTNERSHIP'
),
(
    '00000000-0000-0000-0000-000000000537',
    'AlphaEd 수강생 환불 지연 논란… 소비자원 민원 급증',
    NULL,
    'AlphaEd의 환불 처리 지연에 대한 소비자 불만이 급증하고 있다. 한국소비자원에 접수된 민원이 전월 대비 3배 증가했으며, 약관 개선과 환불 프로세스 자동화를 약속했다.',
    '["AlphaEd","환불","소비자원","CS"]',
    '경쟁사의 CS 이슈는 우리 서비스 품질 차별화의 기회이자, 유사 리스크 점검 계기다.',
    'https://local-seed.example.com/alphaed-refund-issue',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000401',
    CURRENT_TIMESTAMP - INTERVAL '18' DAY,
    0.80,
    'NEGATIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000538',
    'GammaLearn, AWS와 클라우드 자격증 교육 파트너십 체결',
    NULL,
    'GammaLearn이 AWS Korea와 공식 교육 파트너십을 체결했다. AWS 자격증 준비 과정을 GammaLearn 플랫폼에서 독점 제공하며, 기업 단체 수강 할인 프로그램도 함께 출시한다.',
    '["GammaLearn","AWS","자격증","클라우드교육"]',
    '글로벌 클라우드 벤더와의 파트너십은 B2B 영업에서 강력한 레퍼런스가 된다.',
    'https://local-seed.example.com/gammalearn-aws-partnership',
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000402',
    CURRENT_TIMESTAMP - INTERVAL '16' DAY,
    0.86,
    'POSITIVE', 'PARTNERSHIP'
),
(
    '00000000-0000-0000-0000-000000000539',
    'DeltaClass, 시리즈B 브릿지 200억 유치… 기업교육 SaaS 개발 집중',
    NULL,
    'DeltaClass이 시리즈B 브릿지 라운드에서 200억원을 유치했다. 기업교육 SaaS 플랫폼 개발과 AI 콘텐츠 생성 도구에 투자할 예정이며, 연내 흑자 전환을 목표로 한다.',
    '["DeltaClass","투자유치","SaaS","기업교육"]',
    'DeltaClass의 자금 확보는 기업교육 시장에서의 경쟁 심화를 예고한다.',
    'https://local-seed.example.com/deltaclass-bridge-funding',
    FALSE,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000403',
    CURRENT_TIMESTAMP - INTERVAL '13' DAY,
    0.89,
    'POSITIVE', 'FUNDING'
),
(
    '00000000-0000-0000-0000-000000000540',
    '유데미, 한국어 AI 더빙 기능 도입으로 영어 강좌 접근성 강화',
    NULL,
    'Udemy가 AI 기반 한국어 자동 더빙 기능을 도입했다. 영어 강좌 5만여 개를 한국어로 제공할 수 있게 되면서, 국내 에듀테크 플랫폼과의 콘텐츠 경쟁이 심화될 전망이다.',
    '["유데미","AI더빙","한국어","콘텐츠"]',
    'AI 더빙 기술은 언어 장벽을 낮춰 글로벌 플랫폼의 국내 경쟁력을 급격히 높인다.',
    'https://local-seed.example.com/udemy-korean-dubbing',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000404',
    CURRENT_TIMESTAMP - INTERVAL '10' DAY,
    0.88,
    'NEUTRAL', 'PRODUCT_LAUNCH'
),
(
    '00000000-0000-0000-0000-000000000541',
    'BetaCampus 전 CEO, 경쟁사로 이직… 기업교육 업계 긴장',
    NULL,
    'BetaCampus의 전 대표이사가 경쟁 에듀테크 기업으로 이직하면서 업계에 파장이 일고 있다. 핵심 인력 유출과 함께 MegaCorp 그룹사 교육 계약 갱신에 대한 우려도 제기된다.',
    '["BetaCampus","인사이동","CEO","기업교육"]',
    '경영진 이동은 기존 고객 관계와 전략 방향에 변화를 가져올 수 있다.',
    'https://local-seed.example.com/betacampus-ceo-move',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000405',
    CURRENT_TIMESTAMP - INTERVAL '9' DAY,
    0.84,
    'NEGATIVE', 'PERSONNEL'
),
(
    '00000000-0000-0000-0000-000000000542',
    'AlphaEd, 마이크로소프트 Copilot 공인 교육 파트너 선정',
    NULL,
    'AlphaEd가 마이크로소프트의 Copilot 공인 교육 파트너로 선정됐다. Microsoft 365 Copilot 활용 교육과 자격증 과정을 독점 제공하며, 기업 고객 대상 번들 상품도 출시할 예정이다.',
    '["AlphaEd","마이크로소프트","Copilot","공인교육"]',
    '글로벌 빅테크 공인 파트너십은 B2B 영업에서 강력한 경쟁 무기가 된다.',
    'https://local-seed.example.com/alphaed-ms-copilot',
    TRUE,
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000406',
    CURRENT_TIMESTAMP - INTERVAL '7' DAY,
    0.90,
    'POSITIVE', 'PARTNERSHIP'
),
(
    '00000000-0000-0000-0000-000000000543',
    'Inflearn launches enterprise API for LMS integration',
    'GammaLearn, 기업 LMS 연동용 엔터프라이즈 API 출시',
    'GammaLearn이 기업 LMS와 연동할 수 있는 엔터프라이즈 API를 출시했다. SSO, 수강 이력 동기화, 수료증 자동 발급 등의 기능을 제공하며, 대기업 고객 확보에 본격적으로 나선다.',
    '["GammaLearn","API","LMS연동","엔터프라이즈"]',
    'API 기반 연동은 기업 고객의 락인 효과를 강화하는 핵심 전략이다.',
    'https://local-seed.example.com/gammalearn-enterprise-api',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000401',
    CURRENT_TIMESTAMP - INTERVAL '6' DAY,
    0.83,
    'NEUTRAL', 'PRODUCT_LAUNCH'
),
(
    '00000000-0000-0000-0000-000000000544',
    'DeltaClass, 크리에이터 수익 정산 지연으로 집단 소송 위기',
    NULL,
    'DeltaClass이 크리에이터 수익 정산을 3개월 이상 지연하면서 집단 소송 움직임이 나타나고 있다. 100여 명의 크리에이터가 공동 대응을 준비 중이며, 플랫폼 신뢰도에 타격이 예상된다.',
    '["DeltaClass","정산지연","크리에이터","소송"]',
    '정산 이슈는 플랫폼 비즈니스의 근본적 신뢰를 훼손하는 심각한 문제다.',
    'https://local-seed.example.com/deltaclass-settlement-delay',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000402',
    CURRENT_TIMESTAMP - INTERVAL '4' DAY,
    0.92,
    'NEGATIVE', 'OTHER'
),
(
    '00000000-0000-0000-0000-000000000545',
    'Udemy acquires Korean AI startup to bolster adaptive learning',
    '유데미, 한국 AI 스타트업 인수로 적응형 학습 강화',
    'Udemy가 서울 기반 AI 에듀테크 스타트업을 인수했다. 적응형 학습 알고리즘과 한국어 NLP 기술을 확보하며, 아시아 시장 공략을 가속화할 계획이다.',
    '["Udemy","인수","AI스타트업","적응형학습"]',
    '유데미의 국내 스타트업 인수는 기술력 확보와 시장 진입을 동시에 노리는 전략이다.',
    'https://local-seed.example.com/udemy-korean-acquisition',
    TRUE,
    '00000000-0000-0000-0000-000000000202',
    '00000000-0000-0000-0000-000000000403',
    CURRENT_TIMESTAMP - INTERVAL '3' DAY,
    0.94,
    'NEUTRAL', 'FUNDING'
),
(
    '00000000-0000-0000-0000-000000000546',
    'AlphaEd Holdings, AlphaEd 플랫폼에 실시간 화상 강의 기능 추가',
    NULL,
    'AlphaEd Holdings가 AlphaEd에 실시간 화상 강의 기능을 추가했다. Zoom 연동 없이 자체 화상 시스템을 구축하며, 녹화 강의와 라이브 혼합 학습 모델을 제공한다.',
    '["AlphaEd","실시간강의","화상교육","AlphaEd Holdings"]',
    '자체 화상 시스템 구축은 플랫폼 종속성을 줄이고 학습 데이터 확보에 유리하다.',
    'https://local-seed.example.com/alphaed-live-class',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    '00000000-0000-0000-0000-000000000404',
    CURRENT_TIMESTAMP - INTERVAL '15' DAY,
    0.75,
    'POSITIVE', 'PRODUCT_LAUNCH'
),
(
    '00000000-0000-0000-0000-000000000547',
    'MegaCorp BetaCampus, 메타버스 교육 플랫폼 시범 운영 시작',
    NULL,
    'MegaCorp BetaCampus가 메타버스 기반 교육 플랫폼의 시범 운영을 시작했다. 가상 교실과 3D 실습 환경을 제공하며, 제조업 안전교육과 신입사원 온보딩에 우선 적용할 계획이다.',
    '["BetaCampus","메타버스","가상교실","MegaCorp"]',
    '메타버스 교육은 특수 분야(안전·제조)에서 먼저 검증될 가능성이 높다.',
    'https://local-seed.example.com/betacampus-metaverse',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000405',
    CURRENT_TIMESTAMP - INTERVAL '12' DAY,
    0.71,
    'NEUTRAL', 'PRODUCT_LAUNCH'
),
(
    '00000000-0000-0000-0000-000000000548',
    'FastCampus partners with OpenAI for exclusive GPT-based training content',
    'FastCampus, OpenAI와 독점 GPT 교육 콘텐츠 파트너십 체결',
    'FastCampus가 OpenAI와 독점 교육 콘텐츠 파트너십을 체결했다. GPT-4 기반 실습 환경과 프롬프트 엔지니어링 커리큘럼을 공동 개발하며, 아시아 최초 공식 파트너로 선정됐다.',
    '["FastCampus","OpenAI","GPT","파트너십"]',
    'OpenAI 공식 파트너 지위는 AI 교육 시장에서 강력한 브랜드 프리미엄을 제공한다.',
    'https://local-seed.example.com/alphaed-openai-partner',
    TRUE,
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000406',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY,
    0.95,
    'POSITIVE', 'PARTNERSHIP'
),
(
    '00000000-0000-0000-0000-000000000549',
    'BetaCampus, 2026년 상반기 AI 교육 수요 300% 급증 발표',
    NULL,
    'BetaCampus가 2026년 상반기 기업 AI 교육 수요가 전년 동기 대비 300% 급증했다고 밝혔다. 특히 비개발 직군의 생성형 AI 활용 교육이 핵심 성장 동력으로, MegaCorp·SemiCorp 등 대기업 수주가 이어지고 있다.',
    '["BetaCampus","AI교육","기업교육","생성형AI"]',
    'BetaCampus의 AI 교육 수요 급증은 기업교육 시장 내 AI 전환 가속화를 보여주는 신호이다.',
    'https://local-seed.example.com/betacampus-ai-demand',
    FALSE,
    '00000000-0000-0000-0000-000000000207',
    '00000000-0000-0000-0000-000000000407',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY,
    0.88,
    'POSITIVE', 'EARNINGS'
),
(
    '00000000-0000-0000-0000-000000000550',
    'BetaCampus, SearchCo Cloud와 클라우드 인재 양성 MOU 체결',
    NULL,
    'BetaCampus가 SearchCo Cloud와 클라우드 네이티브 인재 양성을 위한 MOU를 체결했다. 연간 1만 명 규모의 클라우드 엔지니어 교육 프로그램을 공동 운영할 계획이다.',
    '["BetaCampus","SearchCo Cloud","클라우드교육","MOU"]',
    '클라우드 인재 양성 파트너십은 기업교육 B2B 경쟁력 강화의 핵심 전략이다.',
    'https://local-seed.example.com/betacampus-searchco-mou',
    FALSE,
    '00000000-0000-0000-0000-000000000208',
    '00000000-0000-0000-0000-000000000408',
    CURRENT_TIMESTAMP - INTERVAL '3' DAY,
    0.82,
    'POSITIVE', 'PARTNERSHIP'
);

-- batch_summaries가 다른 rss_item을 재사용한 경우 로컬 검증용 synthetic rss_item으로 복구한다.
INSERT INTO rss_items (
    id, title, content, link, language, is_processed, category_id, rss_source_id, created_at
)
SELECT
    bs.id,
    bs.original_title,
    bs.summary,
    bs.source_link,
    CASE
        WHEN bs.translated_title IS NULL THEN 'KOREAN'
        ELSE 'FOREIGN'
    END,
    TRUE,
    bs.category_id,
    NULL,
    bs.created_at
FROM batch_summaries bs
JOIN rss_items current_item ON current_item.id = bs.rss_item_id
LEFT JOIN rss_items same_link_item ON same_link_item.link = bs.source_link
WHERE (
    current_item.title <> bs.original_title
    OR current_item.link <> bs.source_link
    OR current_item.category_id <> bs.category_id
)
AND same_link_item.id IS NULL;

UPDATE batch_summaries
SET rss_item_id = (
        SELECT ri.id
        FROM rss_items ri
        WHERE ri.link = batch_summaries.source_link
    ),
    category_id = (
        SELECT ri.category_id
        FROM rss_items ri
        WHERE ri.link = batch_summaries.source_link
    )
WHERE EXISTS (
    SELECT 1
    FROM rss_items ri
    WHERE ri.link = batch_summaries.source_link
)
AND (
    rss_item_id <> (
        SELECT ri.id
        FROM rss_items ri
        WHERE ri.link = batch_summaries.source_link
    )
    OR category_id <> (
        SELECT ri.category_id
        FROM rss_items ri
        WHERE ri.link = batch_summaries.source_link
    )
);

-- 3) daily_summaries: 오늘 + 최근 2일간 카테고리별 일일 요약
DELETE FROM daily_summaries
WHERE id IN (
    '00000000-0000-0000-0000-000000000701',
    '00000000-0000-0000-0000-000000000702',
    '00000000-0000-0000-0000-000000000703',
    '00000000-0000-0000-0000-000000000704',
    '00000000-0000-0000-0000-000000000705',
    '00000000-0000-0000-0000-000000000706',
    '00000000-0000-0000-0000-000000000707',
    '00000000-0000-0000-0000-000000000708',
    '00000000-0000-0000-0000-000000000709',
    '00000000-0000-0000-0000-000000000710',
    '00000000-0000-0000-0000-000000000711',
    '00000000-0000-0000-0000-000000000712'
);

INSERT INTO daily_summaries (id, title, total_items, summary_date, topic_keywords, overall_summary, is_sent_to_slack, category_id, created_at)
VALUES
(
    '00000000-0000-0000-0000-000000000701',
    'AI/테크 뉴스 요약',
    5,
    CURRENT_DATE,
    '["AI 에이전트","Gemini 2.5","개발 생산성","자동화"]',
    '구글이 Gemini 2.5 기반 AI 에이전트를 기업용으로 확대 출시했다. 코드 리뷰와 QA 자동화에 특화된 에이전트가 개발 생산성을 25% 이상 높일 수 있다는 초기 사례가 나오고 있다. 한편 브라우저 내장 AI 어시스턴트 경쟁도 본격화되면서 보안 정책 이슈가 부각되고 있다.',
    FALSE,
    '00000000-0000-0000-0000-000000000201',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000702',
    'HR/교육 동향 요약',
    4,
    CURRENT_DATE,
    '["기업교육","리스킬링","마이크로러닝","HRD"]',
    '기업 교육 시장에서 AI 리스킬링과 마이크로러닝 수요가 동시에 폭발하고 있다. AlphaEd와 BetaCampus가 기업 맞춤 프로그램을 강화하는 한편, GammaLearn은 B2B 진출을 본격화했다. 교육 이탈률 상승이 새로운 과제로 부상 중이다.',
    FALSE,
    '00000000-0000-0000-0000-000000000202',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000703',
    'AI/테크 뉴스 요약',
    3,
    CURRENT_DATE - INTERVAL '1' DAY,
    '["BetaCampus","AI 추천","학습 경로","개인화"]',
    'BetaCampus가 AI 기반 학습 경로 추천 엔진 베타를 출시하면서 에듀테크 AI 경쟁이 심화되고 있다. 개인화 학습의 핵심은 데이터 축적량이며, 기존 대형 플랫폼이 유리한 구조다.',
    TRUE,
    '00000000-0000-0000-0000-000000000201',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY
),
(
    '00000000-0000-0000-0000-000000000704',
    'HR/교육 동향 요약',
    4,
    CURRENT_DATE - INTERVAL '1' DAY,
    '["GammaLearn","B2B","기업교육","업스킬링"]',
    'GammaLearn의 기업 전용 요금제 출시가 B2B 교육 시장에 새로운 변수를 만들고 있다. 비개발 직군 코딩 교육 수요 폭발과 맞물려, 실습 환경 제공이 플랫폼 선택의 핵심 기준이 되고 있다.',
    TRUE,
    '00000000-0000-0000-0000-000000000202',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY
),
(
    '00000000-0000-0000-0000-000000000705',
    '투자/금융 동향 요약',
    2,
    CURRENT_DATE - INTERVAL '2' DAY,
    '["에듀테크","투자유치","시리즈C","AlphaEd"]',
    'AlphaEd 모회사 AlphaEd Holdings가 시리즈C 500억원을 유치하면서 에듀테크 투자 시장이 다시 활기를 띠고 있다. 기업교육 SaaS 분야에 자금이 집중되는 추세다.',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY
),
(
    '00000000-0000-0000-0000-000000000706',
    '규제/정책 동향 요약',
    3,
    CURRENT_DATE - INTERVAL '2' DAY,
    '["법정교육","고용부","규제","온라인교육"]',
    '고용노동부의 법정의무교육 온라인 이수 기준 강화 고시안이 LMS 업계에 파장을 주고 있다. 학습 시간 인증과 평가 연동이 필수화되면서, 플랫폼 업데이트 경쟁이 예상된다. AI 결과물 표시 의무 규제도 SaaS 운영팀의 대응 과제가 되고 있다.',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY
),
(
    '00000000-0000-0000-0000-000000000707',
    '투자/금융 동향 요약',
    4,
    CURRENT_DATE,
    '["금리 인하","성장주","환율","AI 투자"]',
    '미 연준의 추가 금리 인하 기대감이 약화되면서 성장주 중심 포트폴리오 재편이 지연되고 있다. 국내 AI 반도체 기업에 대한 기관 투자는 오히려 증가세를 보이고 있으며, 환율 변동성이 수출 기업 실적 전망을 흔들고 있다.',
    FALSE,
    '00000000-0000-0000-0000-000000000203',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000708',
    '정책/규제 동향 요약',
    3,
    CURRENT_DATE,
    '["AI 규제","데이터 주권","출처 설명","디지털 플랫폼법"]',
    '디지털 플랫폼법 시행령 초안이 공개되면서 플랫폼 기업의 알고리즘 투명성 의무가 구체화되고 있다. AI 결과물 표시 의무와 데이터 출처 설명 책임이 SaaS 운영팀의 최우선 대응 과제로 부상했다.',
    FALSE,
    '00000000-0000-0000-0000-000000000204',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000709',
    '보안/인프라 동향 요약',
    5,
    CURRENT_DATE,
    '["제로트러스트","SBOM","공급망 보안","클라우드 IAM"]',
    '제로트러스트 아키텍처 도입이 금융·공공 부문에서 가속화되고 있다. 서드파티 SDK 보안 점검과 SBOM 의무화가 맞물리면서, 보안팀의 업무 범위가 코드 레벨까지 확장되고 있다. 클라우드 IAM 권한 최소화가 실질적 보안 강화의 출발점으로 재조명되고 있다.',
    FALSE,
    '00000000-0000-0000-0000-000000000205',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000710',
    '마케팅/이커머스 동향 요약',
    4,
    CURRENT_DATE,
    '["퍼포먼스 마케팅","숏폼 커머스","CRM","리텐션"]',
    '숏폼 기반 라이브커머스가 전환율에서 기존 배너 광고를 2배 이상 앞서면서 마케팅 예산 재배분이 빠르게 진행되고 있다. CRM 자동화 도구와 연동한 리텐션 마케팅이 신규 고객 획득 비용 절감의 핵심 전략으로 부상하고 있다.',
    FALSE,
    '00000000-0000-0000-0000-000000000206',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000711',
    '헬스케어/바이오 동향 요약',
    3,
    CURRENT_DATE,
    '["AI 신약","디지털 치료제","원격진료","바이오마커"]',
    'AI 기반 신약 후보 물질이 임상 1상에 진입하면서 디지털 바이오 시대가 본격 개막했다. 디지털 치료제(DTx) 시장도 연 40% 성장이 예상되며, 원격진료 규제 완화와 함께 헬스케어 데이터 표준화가 핵심 과제로 부상하고 있다.',
    FALSE,
    '00000000-0000-0000-0000-000000000207',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000712',
    '핀테크/결제 동향 요약',
    4,
    CURRENT_DATE,
    '["오픈뱅킹","BNPL","결제 수수료","가상자산"]',
    '오픈뱅킹 2.0 시행으로 핀테크 기업의 결제 수수료 인하 경쟁이 본격화되고 있다. BNPL 규제 가이드라인 확정으로 소비자 보호 기준이 높아졌으며, 가상자산 거래소의 원화 입출금 채널 다변화가 새로운 경쟁 축으로 떠오르고 있다.',
    FALSE,
    '00000000-0000-0000-0000-000000000208',
    CURRENT_TIMESTAMP
);

-- 4) competitor_watchlist: 경쟁사 모니터링 대상
DELETE FROM competitor_watchlist WHERE id LIKE '00000000-0000-0000-0000-0000000008%';

INSERT INTO competitor_watchlist (id, name, aliases, exclude_keywords, tier, is_active, created_at, updated_at)
VALUES
(
    '00000000-0000-0000-0000-000000000801',
    'AlphaEd',
    '["AlphaEd","AlphaEd Holdings","FastCampus"]',
    '[]',
    'DIRECT',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000802',
    'BetaCampus',
    '["BetaCampus","크레듀","Multicampus"]',
    '[]',
    'DIRECT',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000803',
    'GammaLearn',
    '["GammaLearn","GammaLearn Labs","Inflearn"]',
    '[]',
    'ADJACENT',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000804',
    'DeltaClass',
    '["DeltaClass","Class101"]',
    '[]',
    'ADJACENT',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000805',
    'Udemy',
    '["유데미","Udemy"]',
    '[]',
    'GLOBAL',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 4-1) competitor_rss_feeds: 경쟁사 수동 RSS 피드
DELETE FROM competitor_rss_feeds WHERE id IN (
    '00000000-0000-0000-0000-000000000851',
    '00000000-0000-0000-0000-000000000852'
);

INSERT INTO competitor_rss_feeds (id, competitor_id, feed_url, label, created_at)
VALUES
(
    '00000000-0000-0000-0000-000000000851',
    '00000000-0000-0000-0000-000000000801',
    'https://blog.alphaed.co.kr/feed',
    '공식 블로그',
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000852',
    '00000000-0000-0000-0000-000000000803',
    'https://tech.gammalearn.com/rss',
    '기술 블로그',
    CURRENT_TIMESTAMP
);

-- 5) original_contents: 기사 원본 본문 시드
DELETE FROM original_contents WHERE rss_item_id IN (
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000402',
    '00000000-0000-0000-0000-000000000403',
    '00000000-0000-0000-0000-000000000404',
    '00000000-0000-0000-0000-000000000405',
    '00000000-0000-0000-0000-000000000406'
);

INSERT INTO original_contents (id, rss_item_id, source_link, title, markdown, content_hash, created_at, updated_at)
VALUES
(
    '00000000-0000-0000-0000-000000000901',
    '00000000-0000-0000-0000-000000000401',
    'https://local-seed.example.com/ai-agent-rollout',
    'Gemini 2.5 기반 AI 에이전트 도입이 사내 개발 속도를 끌어올리고 있다',
    '# Gemini 2.5 기반 AI 에이전트 도입이 사내 개발 속도를 끌어올리고 있다

국내 주요 IT 기업들이 구글의 Gemini 2.5 모델을 기반으로 한 AI 에이전트를 개발 워크플로에 본격 도입하면서, 코드 리뷰부터 QA 자동화까지 개발 주기 전반에 걸친 속도 개선이 이루어지고 있다.

## 도입 배경과 현황

올해 초부터 대형 기술 기업을 중심으로 AI 에이전트 도입이 급격히 확산되고 있다. 기존의 단순한 코드 자동완성 수준을 넘어, 이제는 코드 리뷰, 테스트 케이스 생성, 배포 파이프라인 관리까지 하나의 통합된 워크플로로 자동화하는 방향으로 발전하고 있다.

Acme Corp의 플랫폼 엔지니어링 리드는 "기존에는 코드 리뷰에 평균 2~3일이 걸렸지만, AI 에이전트 도입 후 같은 작업이 수 시간 내로 줄었다"고 밝혔다. 특히 반복적인 패턴의 코드 수정이나 보일러플레이트 코드 생성에서 가장 큰 효과를 보고 있다고 설명했다.

## 운영 효율의 핵심: 승인 프로세스와 로그 추적

다만 현장에서는 AI 모델의 성능보다도 승인 프로세스와 로그 추적 체계를 먼저 정비하는 것이 실질적인 운영 가치를 높이는 핵심이라는 목소리가 높다.

Globex Inc 운영팀 매니저는 "AI가 생성한 코드가 프로덕션에 반영되려면, 누가 승인했고 어떤 근거로 판단했는지 추적할 수 있어야 한다"며 "감사 로그와 승인 체인이 없으면 규제 대응은 물론 내부 보안 감사도 통과하기 어렵다"고 강조했다.

실제로 금융권과 공공기관 프로젝트에서는 AI 생성 코드에 대한 별도의 리뷰 프로세스를 마련하고, 모든 변경 사항에 대한 상세 로그를 의무적으로 남기는 정책을 시행 중이다.

## 비용 대비 효과 분석

AI 에이전트 도입에 따른 비용 효과 분석도 주목할 만하다. 초기 API 호출 비용과 인프라 구축 비용이 발생하지만, 개발자 생산성 향상과 버그 감소에 따른 장기적 비용 절감 효과가 이를 상회한다는 분석이 나오고 있다.

C 컨설팅사의 보고서에 따르면, AI 에이전트를 6개월 이상 운영한 팀의 경우 평균 개발 속도가 35% 향상되었고, 프로덕션 버그 발생률은 22% 감소한 것으로 나타났다. 다만 이러한 수치는 팀의 성숙도와 도입 방식에 따라 편차가 크다는 점도 함께 언급되었다.

## 전문가 전망

업계 전문가들은 AI 에이전트가 단순 보조 도구를 넘어 개발 팀의 핵심 인프라로 자리잡을 것으로 전망한다. 특히 코드 생성, 리뷰, 테스트, 배포까지 이어지는 엔드투엔드 자동화가 2026년 하반기까지 보편화될 것으로 예상되고 있다.

다만 보안, 규제 준수, 지적재산권 등의 이슈가 동시에 부각되고 있어, 기술 도입과 함께 거버넌스 체계를 병행 구축하는 것이 중요하다는 지적도 나온다.

Source: https://local-seed.example.com/ai-agent-rollout',
    'seed-hash-901',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000902',
    '00000000-0000-0000-0000-000000000402',
    'https://local-seed.example.com/browser-ai-copilot',
    'The Verge reports a sharper race around browser-native AI copilots',
    '# 브라우저 네이티브 AI 코파일럿을 둘러싼 경쟁이 심화되고 있다

The Verge의 최신 보도에 따르면, 주요 브라우저 제조사들이 AI 코파일럿 기능을 브라우저에 직접 탑재하는 경쟁이 한층 격화되고 있다. 검색, 문서 요약, 생산성 워크플로를 하나의 인터페이스로 통합하는 방향으로 발전하고 있으며, 기업 고객을 중심으로 보안 정책과 감사 추적 기능이 핵심 차별화 요소로 떠오르고 있다.

## 주요 브라우저별 AI 전략

구글 크롬은 Gemini 모델을 기반으로 탭 그룹 자동 분류, 실시간 페이지 요약, 이메일 초안 작성 등의 기능을 내장형으로 제공하기 시작했다. 마이크로소프트 Edge는 Copilot을 사이드바에 상시 탑재하여 문서 비교, 데이터 추출, 회의록 정리 등을 지원한다.

특히 기업용 시장에서는 브라우저 레벨의 DLP(데이터 손실 방지) 정책 연동과 제로 트러스트 아키텍처 호환성이 채택의 핵심 기준이 되고 있다.

## 기업 고객의 주요 관심사

포춘 500대 기업의 IT 의사결정자 200명을 대상으로 한 설문에서, 브라우저 AI 코파일럿 도입 시 가장 중요하게 고려하는 요소로 다음이 꼽혔다:

1. 데이터 보안 및 프라이버시 정책 (87%)
2. 기존 SSO/IAM 시스템과의 통합 (72%)
3. 감사 로그 및 사용 내역 추적 (68%)
4. 온프레미스/하이브리드 배포 옵션 (54%)
5. 커스텀 모델 파인튜닝 지원 (41%)

## 시장 전망

IDC의 최신 보고서는 2027년까지 글로벌 브라우저 AI 시장 규모가 180억 달러에 이를 것으로 전망하며, 특히 기업용 세그먼트의 연간 성장률이 45%를 상회할 것으로 예측했다.

Source: https://local-seed.example.com/browser-ai-copilot',
    'seed-hash-902',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000903',
    '00000000-0000-0000-0000-000000000403',
    'https://local-seed.example.com/hr-learning-coach',
    '기업 교육팀이 AI 러닝 코치와 마이크로 러닝을 결합해 교육 시간을 줄이고 있다',
    '# 기업 교육팀이 AI 러닝 코치와 마이크로 러닝을 결합해 교육 시간을 줄이고 있다

기업 교육 시장에서 AI 기반 러닝 코치와 마이크로 러닝의 결합이 새로운 트렌드로 자리잡고 있다. 교육 콘텐츠를 5~10분 단위의 짧은 모듈로 쪼개고, AI가 개인별 학습 진도와 취약점을 분석해 맞춤형 피드백을 제공하는 방식이다.

## 도입 사례: Initech 인재개발팀

D사는 올해 1월부터 전 직원 대상 디지털 리터러시 교육에 AI 러닝 코치를 도입했다. 기존 2시간짜리 오프라인 교육을 15개의 마이크로 모듈로 재구성하고, 각 모듈 완료 후 AI가 즉시 개인화된 코멘트를 제공한다.

Initech 인재개발팀장는 "교육 시간이 평균 40% 줄었음에도 불구하고, 교육 후 업무 적용률은 오히려 15% 증가했다"며 "핵심은 단순히 콘텐츠를 줄이는 것이 아니라, AI가 각 직원의 업무 맥락에 맞는 피드백을 실시간으로 제공한다는 점"이라고 설명했다.

## 효과 측정의 중요성

전문가들은 AI 러닝 코치의 효과를 정확히 측정하기 위해서는 수강 완료율뿐만 아니라 현업 전이율(transfer of learning)을 함께 추적해야 한다고 강조한다.

E 연구기관의 조사에 따르면, AI 러닝 코치를 도입한 기업의 수강 완료율은 평균 82%로 전통적 이러닝(58%)보다 24%p 높았다. 그러나 현업 전이율은 도입 기업 간 편차가 크게 나타나(32%~71%), 단순 도입만으로는 충분하지 않으며 운영 모델과 성과 측정 체계의 설계가 병행되어야 한다는 결론이 도출되었다.

## 시장 동향

글로벌 기업 교육 시장에서 AI 기반 러닝 솔루션의 비중은 2025년 12%에서 2026년 23%로 급성장할 전망이다. 국내에서는 에듀사, AlphaEd, BetaCampus 등 주요 교육 기업이 AI 코칭 기능을 핵심 차별화 전략으로 내세우고 있다.

Source: https://local-seed.example.com/hr-learning-coach',
    'seed-hash-903',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000904',
    '00000000-0000-0000-0000-000000000404',
    'https://local-seed.example.com/market-rotation-watch',
    '금리 경로 불확실성이 커지면서 성장주와 현금흐름 우량주 선호가 동시에 나타난다',
    '# 금리 경로 불확실성 속 성장주·현금흐름 우량주 동시 선호 현상

미 연준의 금리 정책 방향에 대한 불확실성이 지속되면서, 글로벌 투자 시장에서는 이례적으로 성장주와 현금흐름 우량주에 대한 선호가 동시에 나타나고 있다. 이는 투자자들이 단순한 방어적 포지션이 아닌, 복합적 리스크 관리 전략을 구사하고 있음을 시사한다.

## 시장 분석

2026년 3월 현재, S&P 500 내 AI 관련 대형 기술주는 올해 들어 평균 18% 상승한 반면, 배당수익률 상위 20% 기업의 주가도 평균 11% 올랐다. 전통적으로 이 두 카테고리는 역의 상관관계를 보이는 경우가 많았으나, 현재는 동반 상승세를 보이고 있다.

글로벌 자산운용사 Hooli의 수석 이코노미스트는 "AI 투자 지출이 실적으로 전환되기 시작하면서 성장주의 밸류에이션이 재평가되고 있고, 동시에 금리 불확실성으로 안정적 현금흐름을 가진 기업에 대한 수요도 유지되는 중"이라고 분석했다.

## 투자위원회의 주요 리스크 인식

주요 기관투자자의 투자위원회에서는 환율과 금리의 동시 변동성을 올해 가장 큰 리스크 팩터로 지목하고 있다. 특히 원/달러 환율이 1,350원대에서 등락을 반복하는 가운데, 한국은행의 기준금리 인하 시점에 대한 전망이 엇갈리고 있어 자산 배분 의사결정이 더욱 복잡해지고 있다.

## 섹터별 전망

반도체: AI 수요 지속으로 긍정적이나, 중국 수출 규제 리스크 상존
바이오: FDA 승인 파이프라인 확대, 하반기 모멘텀 기대
2차전지: 단기 과잉 공급 우려 vs 장기 전기차 전환 수혜
금융: 금리 인하 시 순이자마진 압축 vs 수수료 수익 확대

Source: https://local-seed.example.com/market-rotation-watch',
    'seed-hash-904',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000905',
    '00000000-0000-0000-0000-000000000405',
    'https://local-seed.example.com/regulation-traceability',
    '주요 규제기관이 AI 결과물 표시 의무와 데이터 출처 설명 책임을 강화하는 초안을 검토 중이다',
    '# AI 결과물 표시 의무와 데이터 출처 설명 책임 강화 동향

국내외 주요 규제기관들이 AI 생성 콘텐츠에 대한 표시 의무와 학습 데이터 출처에 대한 설명 책임을 대폭 강화하는 규제 초안을 동시다발적으로 검토하고 있다. 이는 B2B SaaS 서비스와 기업 컨설팅 프로젝트 전반에 큰 영향을 미칠 전망이다.

## 주요 규제 변화 내용

EU AI Act의 시행 세칙이 구체화되면서, 고위험 AI 시스템에 대해서는 학습 데이터의 출처, 전처리 방법, 모델 아키텍처에 대한 상세 문서화가 요구될 예정이다. 국내에서도 과학기술정보통신부가 ''AI 투명성 가이드라인''을 올 상반기 중 발표할 계획으로, 주요 내용은 다음과 같다:

1. AI 생성 콘텐츠에 대한 명시적 라벨링 의무
2. 학습 데이터 출처 및 저작권 정보 공개
3. 모델 의사결정 과정에 대한 설명가능성(Explainability) 확보
4. 정기적 편향성 감사(Bias Audit) 실시 및 결과 공개

## 기업 대응 과제

이러한 규제 변화에 대응하기 위해 기업들은 다음과 같은 선행 과제를 준비해야 한다:

첫째, 운영 문서 체계 정비. AI 모델의 학습 과정, 데이터 파이프라인, 추론 로직에 대한 문서를 표준화된 형식으로 관리해야 한다.

둘째, 로그 보관 정책 수립. AI 시스템의 입력값, 출력값, 중간 판단 과정에 대한 로그를 최소 3년간 보관하는 정책이 필요하다.

셋째, 설명가능성 도구 도입. SHAP, LIME 등의 XAI 도구를 활용해 모델 판단 근거를 비전문가에게도 이해 가능한 수준으로 설명할 수 있어야 한다.

넷째, 크로스펑셔널 거버넌스 구축. 법무, 개인정보보호, 데이터 엔지니어링, ML 엔지니어링 등 다양한 직군이 참여하는 AI 거버넌스 위원회를 운영해야 한다.

## 산업별 영향

금융: 신용평가, 보험 인수 등 기존 AI 활용 영역에서 즉각적 영향 예상
헬스케어: 의료 AI 진단 보조 시스템에 대한 추가 인증 요구 가능성
교육: AI 기반 학습 추천, 자동 채점 시스템의 투명성 요구 증가
미디어: AI 생성 기사, 이미지, 영상에 대한 표시 의무 즉시 적용

Source: https://local-seed.example.com/regulation-traceability',
    'seed-hash-905',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000906',
    '00000000-0000-0000-0000-000000000406',
    'https://local-seed.example.com/sdk-secret-hardening',
    '침해사고 대응팀이 서드파티 SDK와 클라우드 권한 설계를 다시 점검하고 있다',
    '# 서드파티 SDK 보안과 클라우드 권한 설계 재점검 움직임

최근 연이은 보안 사고 분석에서 외부 SDK 연동 권한과 클라우드 비밀값 저장소 구성이 반복적인 취약점으로 지목되면서, 주요 기업의 침해사고 대응팀(CSIRT)이 서드파티 SDK와 클라우드 IAM 권한 설계를 전면 재점검하고 있다.

## 최근 사고 동향

2026년 들어 발생한 주요 보안 사고 중 약 40%가 서드파티 SDK나 오픈소스 라이브러리의 과도한 권한 설정에서 비롯된 것으로 분석되었다. 특히 모바일 앱과 SaaS 서비스에서 사용하는 인증 SDK, 분석 SDK, 결제 SDK 등이 필요 이상의 권한을 요구하는 사례가 빈번하게 발견되고 있다.

Pied Piper의 CISO는 "SDK 하나가 전체 시스템의 보안 수준을 결정할 수 있다"며 "공급망 보안(Supply Chain Security) 관점에서 SDK 도입 전 코드 리뷰와 권한 최소화 원칙 적용이 필수"라고 밝혔다.

## 핵심 점검 항목

현재 각 기업의 보안팀이 집중 점검하고 있는 항목은 다음과 같다:

1. 토큰 구조 개선: 회수 가능한(Revocable) 토큰 체계로 전환. 장기 유효 토큰 사용 금지
2. 비밀값 관리: HashiCorp Vault, AWS Secrets Manager 등 전용 비밀값 관리 도구 필수 사용
3. 최소 권한 원칙: SDK별 필요 권한을 문서화하고, 과도한 권한 요청 시 대안 모색
4. 감사 로그 보강: API 호출, 데이터 접근, 권한 변경에 대한 실시간 감사 로그 수집
5. SBOM 관리: 소프트웨어 자재 명세서(SBOM)를 통한 의존성 가시성 확보

## 클라우드 IAM 설계 가이드

AWS, GCP, Azure 등 주요 클라우드 제공업체도 IAM 보안 강화 가이드를 잇따라 업데이트하고 있다. 공통적으로 강조하는 원칙은:

- 서비스 계정별 최소 권한 할당
- 조건부 액세스 정책(Conditional Access) 적극 활용
- 권한 사용 패턴 분석을 통한 미사용 권한 자동 회수
- 크로스 계정/크로스 리전 접근에 대한 추가 인증 요구

## 업계 대응 현황

금융권에서는 이미 ISMS-P 인증 갱신 시 서드파티 SDK 보안 점검 결과를 필수 제출 항목으로 추가한 상태다. 공공기관은 조달청의 소프트웨어 보안 요구사항에 SBOM 제출을 의무화하는 방안을 검토 중이다.

Source: https://local-seed.example.com/sdk-secret-hardening',
    'seed-hash-906',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 6) keyword_entities: 키워드 엔티티 분류 시드
DELETE FROM keyword_entities WHERE id LIKE '00000000-0000-0000-0000-0000000009%';

INSERT INTO keyword_entities (id, keyword, category, first_seen)
VALUES
('00000000-0000-0000-0000-000000000901', 'AlphaEd', 'ORG', CURRENT_TIMESTAMP - INTERVAL '30' DAY),
('00000000-0000-0000-0000-000000000902', 'BetaCampus', 'ORG', CURRENT_TIMESTAMP - INTERVAL '30' DAY),
('00000000-0000-0000-0000-000000000903', 'GammaLearn', 'ORG', CURRENT_TIMESTAMP - INTERVAL '30' DAY),
('00000000-0000-0000-0000-000000000904', 'DeltaClass', 'ORG', CURRENT_TIMESTAMP - INTERVAL '28' DAY),
('00000000-0000-0000-0000-000000000905', 'Udemy', 'ORG', CURRENT_TIMESTAMP - INTERVAL '28' DAY),
('00000000-0000-0000-0000-000000000906', 'AlphaEd Holdings', 'ORG', CURRENT_TIMESTAMP - INTERVAL '25' DAY),
('00000000-0000-0000-0000-000000000907', 'GammaLearn Labs', 'ORG', CURRENT_TIMESTAMP - INTERVAL '25' DAY),
('00000000-0000-0000-0000-000000000908', 'SearchCo Cloud', 'ORG', CURRENT_TIMESTAMP - INTERVAL '20' DAY),
('00000000-0000-0000-0000-000000000909', 'OpenAI', 'ORG', CURRENT_TIMESTAMP - INTERVAL '15' DAY),
('00000000-0000-0000-0000-000000000910', 'MegaCorp', 'ORG', CURRENT_TIMESTAMP - INTERVAL '20' DAY),
('00000000-0000-0000-0000-000000000911', 'AWS', 'ORG', CURRENT_TIMESTAMP - INTERVAL '10' DAY),
('00000000-0000-0000-0000-000000000912', '마이크로소프트', 'ORG', CURRENT_TIMESTAMP - INTERVAL '10' DAY),
('00000000-0000-0000-0000-000000000913', 'AI교육', 'TECH', CURRENT_TIMESTAMP - INTERVAL '30' DAY),
('00000000-0000-0000-0000-000000000914', '리스킬링', 'TECH', CURRENT_TIMESTAMP - INTERVAL '28' DAY),
('00000000-0000-0000-0000-000000000915', 'LMS', 'TECH', CURRENT_TIMESTAMP - INTERVAL '25' DAY),
('00000000-0000-0000-0000-000000000916', '마이크로러닝', 'TECH', CURRENT_TIMESTAMP - INTERVAL '20' DAY),
('00000000-0000-0000-0000-000000000917', '노코드', 'TECH', CURRENT_TIMESTAMP - INTERVAL '15' DAY),
('00000000-0000-0000-0000-000000000918', 'GPT', 'TECH', CURRENT_TIMESTAMP - INTERVAL '10' DAY),
('00000000-0000-0000-0000-000000000919', 'Copilot', 'TECH', CURRENT_TIMESTAMP - INTERVAL '10' DAY),
('00000000-0000-0000-0000-000000000920', '메타버스', 'TECH', CURRENT_TIMESTAMP - INTERVAL '8' DAY),
('00000000-0000-0000-0000-000000000921', 'AI더빙', 'TECH', CURRENT_TIMESTAMP - INTERVAL '5' DAY),
('00000000-0000-0000-0000-000000000922', '기업교육', 'TOPIC', CURRENT_TIMESTAMP - INTERVAL '30' DAY),
('00000000-0000-0000-0000-000000000923', '에듀테크', 'TOPIC', CURRENT_TIMESTAMP - INTERVAL '28' DAY),
('00000000-0000-0000-0000-000000000924', 'B2B', 'TOPIC', CURRENT_TIMESTAMP - INTERVAL '25' DAY),
('00000000-0000-0000-0000-000000000925', '업스킬링', 'TOPIC', CURRENT_TIMESTAMP - INTERVAL '20' DAY),
('00000000-0000-0000-0000-000000000926', '투자유치', 'TOPIC', CURRENT_TIMESTAMP - INTERVAL '15' DAY),
('00000000-0000-0000-0000-000000000927', '디지털전환', 'TOPIC', CURRENT_TIMESTAMP - INTERVAL '12' DAY),
('00000000-0000-0000-0000-000000000928', '법정교육', 'TOPIC', CURRENT_TIMESTAMP - INTERVAL '10' DAY),
('00000000-0000-0000-0000-000000000929', '고용부', 'ORG', CURRENT_TIMESTAMP - INTERVAL '10' DAY),
('00000000-0000-0000-0000-000000000930', '한국', 'LOCATION', CURRENT_TIMESTAMP - INTERVAL '20' DAY),
('00000000-0000-0000-0000-000000000931', '서울', 'LOCATION', CURRENT_TIMESTAMP - INTERVAL '15' DAY);

-- ============================================================
-- Analytics mock user events (seed-analytics)
-- 21 days of realistic user activity for dashboard testing
-- ============================================================
DELETE FROM user_events WHERE session_id = 'seed-analytics';

INSERT INTO user_events (user_id, event_type, event_data, page_path, session_id, created_at) VALUES
-- ====== Day -20 (Mon) — 4 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:05:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:06:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:06:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:07:00'),
('00000000-0000-0000-0000-000000000001','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:08:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:30:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:31:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:31:10'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:31:20'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:32:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '10:00:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '10:01:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '10:01:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '10:02:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '12:10:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '12:11:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '12:11:10'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '12:11:20'),

-- ====== Day -19 (Tue) — 5 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:10:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:11:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:11:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:12:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:20:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:21:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:21:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:22:00'),
('00000000-0000-0000-0000-000000000002','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:23:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '10:15:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '10:16:00'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '10:17:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '12:05:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '12:06:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '12:06:10'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '12:07:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '15:00:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '15:01:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '15:01:10'),

-- ====== Day -18 (Wed) — 4 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:00:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:01:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:01:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:02:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:45:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:46:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:46:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:47:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '12:30:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '12:31:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '12:31:10'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '12:32:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '15:20:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '15:21:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '15:21:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '15:22:00'),
('00000000-0000-0000-0000-000000000008','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '15:23:00'),

-- ====== Day -17 (Thu) — 5 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:08:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:09:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:09:10'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:09:20'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:10:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:12:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:25:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:26:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:26:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:27:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '10:05:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '10:06:00'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '10:07:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '12:15:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '12:16:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '12:16:10'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '12:17:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '15:30:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '15:31:00'),
('00000000-0000-0000-0000-000000000008','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '15:32:00'),

-- ====== Day -16 (Fri) — 4 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:15:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:16:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:16:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:17:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:40:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:41:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:41:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:42:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '12:00:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '12:01:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '12:01:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '12:02:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '15:10:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '15:11:00'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '15:12:00'),
('00000000-0000-0000-0000-000000000007','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '15:13:00'),

-- ====== Day -15 (Sat) — 2 users (weekend) ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '10:30:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '10:31:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '10:31:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '10:32:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '14:00:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '14:01:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '14:01:10'),

-- ====== Day -14 (Sun) — 1 user (weekend) ======
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '14' DAY + TIME '11:00:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '14' DAY + TIME '11:01:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '14' DAY + TIME '11:01:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '14' DAY + TIME '11:02:00'),

-- ====== Day -13 (Mon, Week 2) — 5 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:02:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:03:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:03:10'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:03:20'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:04:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:06:00'),
('00000000-0000-0000-0000-000000000001','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:07:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:20:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:21:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:21:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:22:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '10:10:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '10:11:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '10:11:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '10:12:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '12:20:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '12:21:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '12:21:10'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '12:22:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '15:05:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '15:06:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '15:06:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '15:07:00'),

-- ====== Day -12 (Tue) — 5 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:05:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:06:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:06:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:07:00'),
('00000000-0000-0000-0000-000000000001','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:08:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:35:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:36:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:36:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:37:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '10:20:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '10:21:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '10:21:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '10:22:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '12:10:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '12:11:00'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '12:12:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '15:15:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '15:16:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '15:16:10'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '15:17:00'),

-- ====== Day -11 (Wed) — 4 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:12:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:13:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:13:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:14:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:50:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:51:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:51:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:52:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '12:30:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '12:31:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '12:31:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '12:32:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '15:40:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '15:41:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '15:41:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '15:42:00'),

-- ====== Day -10 (Thu) — 6 users (peak day) ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:00:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:01:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:01:10'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:01:20'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:02:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:04:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:15:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:16:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:16:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:17:00'),
('00000000-0000-0000-0000-000000000002','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:18:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '10:00:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '10:01:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '10:01:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '10:02:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '12:05:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '12:06:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '12:06:10'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '12:07:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:00:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:01:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:01:10'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:02:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:30:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:31:00'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:32:00'),
('00000000-0000-0000-0000-000000000008','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:33:00'),

-- ====== Day -9 (Fri) — 3 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '09:20:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '09:21:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '09:21:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '09:22:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '12:00:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '12:01:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '12:01:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '12:02:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '15:45:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '15:46:00'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '15:47:00'),

-- ====== Day -8 (Sat, weekend) — 2 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '8' DAY + TIME '11:00:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '8' DAY + TIME '11:01:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '8' DAY + TIME '11:02:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '8' DAY + TIME '14:30:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '8' DAY + TIME '14:31:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '8' DAY + TIME '14:31:10'),

-- ====== Day -7 (Sun, weekend) — 1 user ======
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '7' DAY + TIME '16:00:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '7' DAY + TIME '16:01:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '7' DAY + TIME '16:01:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '7' DAY + TIME '16:02:00'),

-- ====== Day -6 (Mon, Week 3 — current week, MORE activity) — 6 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:00:00'),
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:01:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:02:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:02:10'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:02:20'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:03:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:05:00'),
('00000000-0000-0000-0000-000000000001','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:06:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:15:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:16:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:16:10'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:16:20'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:17:00'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:19:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '10:00:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '10:01:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '10:01:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '10:02:00'),
('00000000-0000-0000-0000-000000000005','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '10:03:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '12:10:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '12:11:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '12:11:10'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '12:12:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:00:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:01:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:01:10'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:02:00'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:04:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:30:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:31:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:31:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:32:00'),

-- ====== Day -5 (Tue) — 5 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:05:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:06:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:06:10'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:06:20'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:07:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:09:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:30:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:31:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:31:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:32:00'),
('00000000-0000-0000-0000-000000000002','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:33:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '10:15:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '10:16:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '10:16:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '10:17:00'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '10:19:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '12:20:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '12:21:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '12:21:10'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '12:22:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '15:40:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '15:41:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '15:41:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '15:42:00'),

-- ====== Day -4 (Wed) — 5 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:10:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:11:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:11:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:12:00'),
('00000000-0000-0000-0000-000000000001','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:13:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:40:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:41:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:41:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:42:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '12:00:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '12:01:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '12:01:10'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '12:02:00'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '12:04:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:10:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:11:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:11:10'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:12:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:50:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:51:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:51:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:52:00'),

-- ====== Day -3 (Thu) — 6 users (peak) ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:00:00'),
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:01:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:02:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:02:10'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:02:20'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:03:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:05:00'),
('00000000-0000-0000-0000-000000000001','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:06:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:20:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:21:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:21:10'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:21:20'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:22:00'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:24:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '10:05:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '10:06:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '10:06:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '10:07:00'),
('00000000-0000-0000-0000-000000000005','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '10:08:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '12:00:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '12:01:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '12:01:10'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '12:02:00'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '12:04:00'),
('00000000-0000-0000-0000-000000000007','page_view',NULL,'/user/clipping/articles','seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:00:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:01:00'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:01:10'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:02:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:45:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:46:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:46:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:47:00'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:49:00'),

-- ====== Day -2 (Fri) — 4 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:08:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:09:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:09:10'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:09:20'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:10:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:12:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:30:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:31:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:31:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:32:00'),
('00000000-0000-0000-0000-000000000002','feature_use','{"feature":"search"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:33:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '12:15:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '12:16:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '12:16:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '12:17:00'),
('00000000-0000-0000-0000-000000000008','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '15:30:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '15:31:00'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '15:31:10'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '15:32:00'),

-- ====== Day -1 (Sat, weekend) — 3 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '10:30:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '10:31:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '10:31:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '10:32:00'),
('00000000-0000-0000-0000-000000000002','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '13:00:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '13:01:00'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '13:01:10'),
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '13:02:00'),
('00000000-0000-0000-0000-000000000006','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '15:00:00'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '15:01:00'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '15:02:00'),

-- ====== Day 0 (Today, Sun) — 2 users ======
('00000000-0000-0000-0000-000000000001','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE + TIME '09:00:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE + TIME '09:01:00'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE + TIME '09:01:10'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE + TIME '09:02:00'),
('00000000-0000-0000-0000-000000000005','page_view',NULL,'/user/clipping','seed-analytics',CURRENT_DATE + TIME '10:30:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE + TIME '10:31:00'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE + TIME '10:31:10'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000513","title":"GammaLearn, 월간 활성 사용자 200만"}',NULL,'seed-analytics',CURRENT_DATE + TIME '10:32:00'),
('00000000-0000-0000-0000-000000000005','feature_use','{"feature":"bookmark"}',NULL,'seed-analytics',CURRENT_DATE + TIME '10:33:00'),

-- ====== Additional impression-only events (boost impression:click ratio to ~3:1) ======
-- Day -20 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:07:30'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:07:40'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '09:32:10'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '20' DAY + TIME '10:02:30'),

-- Day -19 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:12:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '09:22:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '10:17:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '15:02:00'),

-- Day -18 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:02:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:47:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '12:32:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '15:22:30'),

-- Day -17 extra impressions
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '09:27:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '10:07:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '12:17:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '15:31:30'),

-- Day -16 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:17:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '09:42:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000502","title":"browser-native AI copilots"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '12:02:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '16' DAY + TIME '15:12:30'),

-- Day -13 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:04:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '09:22:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '10:12:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '12:22:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '15:07:30'),

-- Day -12 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:07:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '09:37:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '10:22:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '15:17:30'),

-- Day -11 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:14:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '09:52:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '12:32:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '15:42:30'),

-- Day -10 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:02:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '09:17:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '10:02:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '12:07:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:02:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:32:30'),

-- Day -9 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '09:22:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '12:02:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '15:47:30'),

-- Day -6 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:03:30'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:03:40'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:17:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '10:02:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '12:12:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:02:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '15:32:30'),

-- Day -5 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:07:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '09:32:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '10:17:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '12:22:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000501","title":"Gemini 2.5 기반 AI 에이전트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '15:42:30'),

-- Day -4 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:12:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:42:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '12:04:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:12:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '15:52:30'),

-- Day -3 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:03:30'),
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:03:40'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '09:22:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '10:07:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '12:02:30'),
('00000000-0000-0000-0000-000000000007','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:02:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:47:30'),

-- Day -2 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:10:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000534","title":"DeltaClass CTO 영입"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:32:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '12:17:30'),
('00000000-0000-0000-0000-000000000008','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '15:32:30'),

-- Day -1 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '10:32:30'),
('00000000-0000-0000-0000-000000000002','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '13:02:30'),
('00000000-0000-0000-0000-000000000006','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '1' DAY + TIME '15:02:30'),

-- Day 0 extra impressions
('00000000-0000-0000-0000-000000000001','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE + TIME '09:02:30'),
('00000000-0000-0000-0000-000000000005','article_impression','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE + TIME '10:32:30'),

-- ====== Additional non-AI-category clicks (rebalance toward ~40% AI) ======
-- More HR/L&D clicks (503, 511, 516, 535)
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '15' DAY + TIME '14:02:00'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000511","title":"AlphaEd AI 리스킬링"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '19' DAY + TIME '15:03:00'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '15:43:00'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '9' DAY + TIME '15:48:00'),
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000535","title":"유데미 글로벌 스킬 인사이트"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '8' DAY + TIME '14:32:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000503","title":"AI 러닝 코치와 마이크로 러닝"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '4' DAY + TIME '09:13:30'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000516","title":"마이크로러닝 플랫폼 시장"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '15:33:00'),

-- More 투자/금융 clicks (504)
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '12:23:00'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '15:18:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000504","title":"금리 경로 불확실성"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '2' DAY + TIME '09:13:00'),

-- More 정책/규제 clicks (505)
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '5' DAY + TIME '12:23:00'),
('00000000-0000-0000-0000-000000000006','article_click','{"summaryId":"00000000-0000-0000-0000-000000000505","title":"AI 결과물 표시 의무"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '13' DAY + TIME '12:23:30'),

-- More 보안/인프라 clicks (506)
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '17' DAY + TIME '10:08:00'),
('00000000-0000-0000-0000-000000000001','article_click','{"summaryId":"00000000-0000-0000-0000-000000000506","title":"침해사고 대응팀"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '6' DAY + TIME '09:04:00'),

-- More 헬스케어/바이오 clicks (507)
('00000000-0000-0000-0000-000000000002','article_click','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '18' DAY + TIME '09:48:00'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '10' DAY + TIME '15:03:00'),
('00000000-0000-0000-0000-000000000008','article_click','{"summaryId":"00000000-0000-0000-0000-000000000507","title":"AI 기반 신약 후보 발굴"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '3' DAY + TIME '15:48:00'),

-- More 핀테크/결제 clicks (508)
('00000000-0000-0000-0000-000000000005','article_click','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '11' DAY + TIME '12:33:00'),
('00000000-0000-0000-0000-000000000007','article_click','{"summaryId":"00000000-0000-0000-0000-000000000508","title":"오픈뱅킹 2.0"}',NULL,'seed-analytics',CURRENT_DATE - INTERVAL '12' DAY + TIME '15:18:30');

-- ============================================================
-- 추가 시드 데이터: 카테고리, RSS 소스, 파이프라인 실행, 통계 보강
-- ============================================================

-- 추가 카테고리 (209-210): 스타트업/ESG/클라우드/고객경험으로 10개 채우기
UPDATE batch_categories
SET name = '스타트업/VC',
    description = '스타트업 투자, VC 동향, 창업 생태계 트렌드',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000102',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000209';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000209',
    '스타트업/VC',
    '스타트업 투자, VC 동향, 창업 생태계 트렌드',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000102',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000209'
);

UPDATE batch_categories
SET name = 'ESG/클라우드',
    description = '탄소중립, ESG 경영, 클라우드 인프라 전환 트렌드',
    slack_channel_id = 'C1234567890',
    is_active = TRUE,
    max_items = 5,
    persona_id = '00000000-0000-0000-0000-000000000103',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000210';

INSERT INTO batch_categories (
    id, name, description, slack_channel_id, is_active, max_items, persona_id, created_at, updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000210',
    'ESG/클라우드',
    '탄소중립, ESG 경영, 클라우드 인프라 전환 트렌드',
    'C1234567890',
    TRUE,
    5,
    '00000000-0000-0000-0000-000000000103',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM batch_categories WHERE id = '00000000-0000-0000-0000-000000000210'
);

-- 추가 RSS 소스 (317-330): 각 카테고리별 2개씩 보강해 20개+ 달성
-- 보안/인프라 추가 소스
UPDATE rss_sources
SET name = 'Krebs On Security',
    url = 'https://krebsonsecurity.com/feed/',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000205',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 90,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'GLOBAL',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000317';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000317',
    'Krebs On Security',
    'https://krebsonsecurity.com/feed/',
    NULL, TRUE, '00000000-0000-0000-0000-000000000205',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 90,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'GLOBAL'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000317');

-- 마케팅/이커머스 추가 소스
UPDATE rss_sources
SET name = 'eMarketer / Insider Intelligence',
    url = 'https://www.emarketer.com/rss/articles.xml',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000206',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 83,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'GLOBAL',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000318';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000318',
    'eMarketer / Insider Intelligence',
    'https://www.emarketer.com/rss/articles.xml',
    NULL, TRUE, '00000000-0000-0000-0000-000000000206',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 83,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'GLOBAL'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000318');

-- 헬스케어/바이오 추가 소스
UPDATE rss_sources
SET name = 'STAT News',
    url = 'https://www.statnews.com/feed/',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000207',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 87,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'GLOBAL',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000319';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000319',
    'STAT News',
    'https://www.statnews.com/feed/',
    NULL, TRUE, '00000000-0000-0000-0000-000000000207',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 87,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'GLOBAL'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000319');

-- 핀테크/결제 추가 소스
UPDATE rss_sources
SET name = 'Finextra',
    url = 'https://www.finextra.com/rss/channel.aspx',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000208',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 85,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'GLOBAL',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000320';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000320',
    'Finextra',
    'https://www.finextra.com/rss/channel.aspx',
    NULL, TRUE, '00000000-0000-0000-0000-000000000208',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 85,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'GLOBAL'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000320');

-- 스타트업/VC 소스
UPDATE rss_sources
SET name = 'Google News - 스타트업/투자',
    url = 'https://news.google.com/rss/search?q=스타트업+투자+VC&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000209',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 82,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000321';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000321',
    'Google News - 스타트업/투자',
    'https://news.google.com/rss/search?q=스타트업+투자+VC&hl=ko&gl=KR&ceid=KR:ko',
    NULL, TRUE, '00000000-0000-0000-0000-000000000209',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 82,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'DOMESTIC'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000321');

UPDATE rss_sources
SET name = 'TechCrunch',
    url = 'https://techcrunch.com/feed/',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000209',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 88,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'GLOBAL',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000322';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000322',
    'TechCrunch',
    'https://techcrunch.com/feed/',
    NULL, TRUE, '00000000-0000-0000-0000-000000000209',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 88,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'GLOBAL'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000322');

-- ESG/클라우드 소스
UPDATE rss_sources
SET name = 'Google News - ESG/탄소중립',
    url = 'https://news.google.com/rss/search?q=ESG+탄소중립+지속가능경영&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000210',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 81,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000323';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000323',
    'Google News - ESG/탄소중립',
    'https://news.google.com/rss/search?q=ESG+탄소중립+지속가능경영&hl=ko&gl=KR&ceid=KR:ko',
    NULL, TRUE, '00000000-0000-0000-0000-000000000210',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 81,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'DOMESTIC'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000323');

UPDATE rss_sources
SET name = 'Google News - 클라우드/인프라',
    url = 'https://news.google.com/rss/search?q=클라우드+인프라+AWS+Azure&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000210',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 84,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000324';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000324',
    'Google News - 클라우드/인프라',
    'https://news.google.com/rss/search?q=클라우드+인프라+AWS+Azure&hl=ko&gl=KR&ceid=KR:ko',
    NULL, TRUE, '00000000-0000-0000-0000-000000000210',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 84,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'DOMESTIC'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000324');

-- AI/테크 추가 소스 (MIT Tech Review)
UPDATE rss_sources
SET name = 'MIT Technology Review',
    url = 'https://www.technologyreview.com/feed/',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000201',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 92,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'GLOBAL',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000325';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000325',
    'MIT Technology Review',
    'https://www.technologyreview.com/feed/',
    NULL, TRUE, '00000000-0000-0000-0000-000000000201',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 92,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'GLOBAL'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000325');

-- 투자/금융 추가 소스 (Bloomberg Technology RSS proxy)
UPDATE rss_sources
SET name = 'Google News - 주식/채권/금융시장',
    url = 'https://news.google.com/rss/search?q=주식+채권+금융시장+실적&hl=ko&gl=KR&ceid=KR:ko',
    is_active = TRUE,
    category_id = '00000000-0000-0000-0000-000000000203',
    crawl_approved = TRUE,
    approved_by = 'seed-script',
    approved_at = CURRENT_TIMESTAMP,
    verification_status = 'VERIFIED',
    reliability_score = 83,
    legal_basis = 'QUOTATION_ONLY',
    summary_allowed = TRUE,
    fulltext_allowed = FALSE,
    terms_reviewed_at = CURRENT_TIMESTAMP,
    review_notes = '로컬 검증용 시드 데이터',
    crawl_fail_count = 0,
    source_region = 'DOMESTIC',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '00000000-0000-0000-0000-000000000326';

INSERT INTO rss_sources (
    id, name, url, emoji, is_active, category_id, created_at, updated_at, crawl_approved,
    approved_by, approved_at, verification_status, reliability_score, legal_basis, summary_allowed,
    fulltext_allowed, terms_reviewed_at, review_notes, crawl_fail_count, source_region
)
SELECT
    '00000000-0000-0000-0000-000000000326',
    'Google News - 주식/채권/금융시장',
    'https://news.google.com/rss/search?q=주식+채권+금융시장+실적&hl=ko&gl=KR&ceid=KR:ko',
    NULL, TRUE, '00000000-0000-0000-0000-000000000203',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    TRUE, 'seed-script', CURRENT_TIMESTAMP, 'VERIFIED', 83,
    'QUOTATION_ONLY', TRUE, FALSE, CURRENT_TIMESTAMP, '로컬 검증용 시드 데이터', 0, 'DOMESTIC'
WHERE NOT EXISTS (SELECT 1 FROM rss_sources WHERE id = '00000000-0000-0000-0000-000000000326');

-- ============================================================
-- pipeline_runs: 1 성공 + 1 실패 시나리오
-- ============================================================
DELETE FROM pipeline_step_traces WHERE run_id IN (
    '00000000-0000-0000-0000-000000001101',
    '00000000-0000-0000-0000-000000001102',
    '00000000-0000-0000-0000-000000001103'
);
DELETE FROM pipeline_runs WHERE id IN (
    '00000000-0000-0000-0000-000000001101',
    '00000000-0000-0000-0000-000000001102',
    '00000000-0000-0000-0000-000000001103'
);

INSERT INTO pipeline_runs (
    id, category_id, category_name, triggered_by, status, orchestration_mode,
    total_collected, total_summarized, total_digest_selected, posted_to_slack,
    started_at, ended_at, duration_ms, error_message, created_at
)
VALUES
(
    '00000000-0000-0000-0000-000000001101',
    '00000000-0000-0000-0000-000000000201',
    'AI/테크',
    'scheduler',
    'SUCCEEDED',
    'FULL',
    16, 10, 5, TRUE,
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:00:00',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:04:32',
    272000,
    NULL,
    CURRENT_TIMESTAMP - INTERVAL '2' DAY
),
(
    '00000000-0000-0000-0000-000000001102',
    '00000000-0000-0000-0000-000000000205',
    '보안/인프라',
    'scheduler',
    'FAILED',
    'FULL',
    8, 0, 0, FALSE,
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:00:00',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:01:15',
    75000,
    'OpenAI API timeout after 3 retries: context length exceeded for source 00000000-0000-0000-0000-000000000309',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY
),
(
    '00000000-0000-0000-0000-000000001103',
    '00000000-0000-0000-0000-000000000202',
    'HR/L&D',
    'scheduler',
    'SUCCEEDED',
    'FULL',
    9, 6, 3, TRUE,
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:00:00',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:03:10',
    190000,
    NULL,
    CURRENT_TIMESTAMP - INTERVAL '1' DAY
);

INSERT INTO pipeline_step_traces (
    id, run_id, step, status, started_at, ended_at, duration_ms, detail, created_at
)
VALUES
-- 성공 파이프라인 (1101) 단계별 추적
(
    '00000000-0000-0000-0000-000000001201',
    '00000000-0000-0000-0000-000000001101',
    'COLLECT',
    'SUCCEEDED',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:00:00',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:01:10',
    70000,
    '소스 2개에서 16건 수집 완료',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY
),
(
    '00000000-0000-0000-0000-000000001202',
    '00000000-0000-0000-0000-000000001101',
    'SUMMARIZE',
    'SUCCEEDED',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:01:10',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:03:30',
    140000,
    '16건 중 10건 요약 완료 (6건 중복/저품질 제외)',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY
),
(
    '00000000-0000-0000-0000-000000001203',
    '00000000-0000-0000-0000-000000001101',
    'DIGEST',
    'SUCCEEDED',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:03:30',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:04:10',
    40000,
    '상위 5건 선별 완료',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY
),
(
    '00000000-0000-0000-0000-000000001204',
    '00000000-0000-0000-0000-000000001101',
    'DELIVER',
    'SUCCEEDED',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:04:10',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY + TIME '09:04:32',
    22000,
    'Slack 채널 C1234567890 발송 완료',
    CURRENT_TIMESTAMP - INTERVAL '2' DAY
),
-- 실패 파이프라인 (1102) 단계별 추적
(
    '00000000-0000-0000-0000-000000001205',
    '00000000-0000-0000-0000-000000001102',
    'COLLECT',
    'SUCCEEDED',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:00:00',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:00:45',
    45000,
    '보안 소스 1개에서 8건 수집 완료',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY
),
(
    '00000000-0000-0000-0000-000000001206',
    '00000000-0000-0000-0000-000000001102',
    'SUMMARIZE',
    'FAILED',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:00:45',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY + TIME '09:01:15',
    30000,
    'OpenAI API 타임아웃: 컨텍스트 길이 초과 (3회 재시도 후 실패)',
    CURRENT_TIMESTAMP - INTERVAL '1' DAY
);

-- ============================================================
-- 7일치 clipping_stats 보강 (기존 3일치 → 7일치)
-- 카테고리: AI/테크(201), HR/L&D(202), 투자/금융(203), 보안/인프라(205)
-- ============================================================
DELETE FROM clipping_stats WHERE id IN (
    '00000000-0000-0000-0000-000000001301',
    '00000000-0000-0000-0000-000000001302',
    '00000000-0000-0000-0000-000000001303',
    '00000000-0000-0000-0000-000000001304',
    '00000000-0000-0000-0000-000000001305',
    '00000000-0000-0000-0000-000000001306',
    '00000000-0000-0000-0000-000000001307',
    '00000000-0000-0000-0000-000000001308',
    '00000000-0000-0000-0000-000000001309',
    '00000000-0000-0000-0000-000000001310',
    '00000000-0000-0000-0000-000000001311',
    '00000000-0000-0000-0000-000000001312',
    '00000000-0000-0000-0000-000000001313',
    '00000000-0000-0000-0000-000000001314',
    '00000000-0000-0000-0000-000000001315',
    '00000000-0000-0000-0000-000000001316',
    '00000000-0000-0000-0000-000000001317',
    '00000000-0000-0000-0000-000000001318',
    '00000000-0000-0000-0000-000000001319',
    '00000000-0000-0000-0000-000000001320'
);

INSERT INTO clipping_stats (
    id, category_id, stat_date, items_collected, items_summarized, items_sent,
    top_keywords, avg_importance_score, created_at, items_duplicates, slack_send_attempts, slack_send_successes
)
VALUES
-- AI/테크 (201): 최근 7일
('00000000-0000-0000-0000-000000001301', '00000000-0000-0000-0000-000000000201', CURRENT_DATE - 6, 14, 9, 4, '["AI 에이전트","LLM","Gemini"]', 0.81, CURRENT_TIMESTAMP, 3, 4, 4),
('00000000-0000-0000-0000-000000001302', '00000000-0000-0000-0000-000000000201', CURRENT_DATE - 5, 11, 7, 3, '["OpenAI","GPT-5","추론모델"]', 0.78, CURRENT_TIMESTAMP, 2, 3, 3),
('00000000-0000-0000-0000-000000001303', '00000000-0000-0000-0000-000000000201', CURRENT_DATE - 4, 15, 10, 5, '["AI 에이전트","Copilot","자동화"]', 0.84, CURRENT_TIMESTAMP, 2, 5, 5),
('00000000-0000-0000-0000-000000001304', '00000000-0000-0000-0000-000000000201', CURRENT_DATE - 3, 18, 12, 6, '["Gemini 2.5","멀티모달","개발 생산성"]', 0.86, CURRENT_TIMESTAMP, 3, 6, 5),
-- HR/L&D (202): 최근 7일
('00000000-0000-0000-0000-000000001305', '00000000-0000-0000-0000-000000000202', CURRENT_DATE - 6, 8, 5, 2, '["기업교육","리스킬링","HRD"]', 0.68, CURRENT_TIMESTAMP, 1, 2, 2),
('00000000-0000-0000-0000-000000001306', '00000000-0000-0000-0000-000000000202', CURRENT_DATE - 5, 7, 4, 2, '["마이크로러닝","학습 분석","LXP"]', 0.65, CURRENT_TIMESTAMP, 1, 2, 2),
('00000000-0000-0000-0000-000000001307', '00000000-0000-0000-0000-000000000202', CURRENT_DATE - 4, 10, 6, 3, '["업스킬링","AI 코칭","비개발직군"]', 0.71, CURRENT_TIMESTAMP, 2, 3, 3),
('00000000-0000-0000-0000-000000001308', '00000000-0000-0000-0000-000000000202', CURRENT_DATE - 3, 9, 5, 3, '["원격학습","하이브리드","게이미피케이션"]', 0.69, CURRENT_TIMESTAMP, 1, 3, 3),
-- 투자/금융 (203): 최근 7일
('00000000-0000-0000-0000-000000001309', '00000000-0000-0000-0000-000000000203', CURRENT_DATE - 6, 12, 7, 3, '["금리","채권","실적발표"]', 0.63, CURRENT_TIMESTAMP, 2, 3, 3),
('00000000-0000-0000-0000-000000001310', '00000000-0000-0000-0000-000000000203', CURRENT_DATE - 5, 10, 6, 3, '["연준","환율","반도체주"]', 0.66, CURRENT_TIMESTAMP, 2, 3, 3),
('00000000-0000-0000-0000-000000001311', '00000000-0000-0000-0000-000000000203', CURRENT_DATE - 4, 14, 8, 4, '["AI투자","성장주","포트폴리오"]', 0.70, CURRENT_TIMESTAMP, 2, 4, 4),
('00000000-0000-0000-0000-000000001312', '00000000-0000-0000-0000-000000000203', CURRENT_DATE - 3, 11, 7, 4, '["IPO","PE","VC라운드"]', 0.67, CURRENT_TIMESTAMP, 2, 4, 4),
-- 보안/인프라 (205): 최근 7일
('00000000-0000-0000-0000-000000001313', '00000000-0000-0000-0000-000000000205', CURRENT_DATE - 6, 9, 6, 3, '["CVE","취약점","패치"]', 0.80, CURRENT_TIMESTAMP, 1, 3, 3),
('00000000-0000-0000-0000-000000001314', '00000000-0000-0000-0000-000000000205', CURRENT_DATE - 5, 11, 7, 3, '["제로트러스트","IAM","SBOM"]', 0.82, CURRENT_TIMESTAMP, 2, 3, 3),
('00000000-0000-0000-0000-000000001315', '00000000-0000-0000-0000-000000000205', CURRENT_DATE - 4, 8, 5, 2, '["클라우드 보안","권한 최소화","시크릿관리"]', 0.79, CURRENT_TIMESTAMP, 1, 2, 2),
('00000000-0000-0000-0000-000000001316', '00000000-0000-0000-0000-000000000205', CURRENT_DATE - 3, 0, 0, 0, '[]', 0.00, CURRENT_TIMESTAMP, 0, 0, 0)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 9) 감사 로그 (audit_log) — 최근 7일 어드민 액션 15건
-- ============================================================
-- audit_log uses BIGSERIAL PK; use DELETE+INSERT with fixed IDs for idempotency
DELETE FROM audit_log WHERE id BETWEEN 9001 AND 9015;

-- actor_id 는 admin_users.id (UUID) 를 참조하도록 통일한다. 표시명은 actor_name 으로만 전달하고,
-- 모든 seed row 는 dev.admin@clipping.local (-0001) 이 수행한 것으로 기록한다.
-- 이 일관성은 V117 에서 추가하는 FK (audit_log.actor_id → admin_users.id) 와 전제 조건.
INSERT INTO audit_log (id, actor_id, actor_name, action, target_type, target_id, target_name, detail, created_at)
VALUES
-- 카테고리 관리
(9001, '00000000-0000-0000-0000-000000000001', 'Admin User', 'CREATE', 'CATEGORY', '00000000-0000-0000-0000-000000000208', '뉴스레터/트렌드', '새 카테고리 "뉴스레터/트렌드" 생성', CURRENT_TIMESTAMP - INTERVAL '6' DAY),
(9002, '00000000-0000-0000-0000-000000000001', 'Admin User', 'UPDATE', 'CATEGORY', '00000000-0000-0000-0000-000000000201', 'AI/테크', '최대 아이템 수 변경: 5 → 8', CURRENT_TIMESTAMP - INTERVAL '6' DAY + INTERVAL '2' HOUR),
(9003, '00000000-0000-0000-0000-000000000001', 'Operator User', 'UPDATE', 'CATEGORY', '00000000-0000-0000-0000-000000000203', '투자/금융', '발송 시간 변경: 09:00 → 08:00', CURRENT_TIMESTAMP - INTERVAL '5' DAY),
(9004, '00000000-0000-0000-0000-000000000001', 'Admin User', 'DELETE', 'CATEGORY', '00000000-0000-0000-0000-000000000209', '글로벌 핀테크', '비활성화된 카테고리 삭제', CURRENT_TIMESTAMP - INTERVAL '5' DAY + INTERVAL '3' HOUR),
-- 소스 승인
(9005, '00000000-0000-0000-0000-000000000001', 'Operator User', 'APPROVE', 'SOURCE', '00000000-0000-0000-0000-000000000301', 'TechCrunch Korea', '소스 검토 후 승인 처리', CURRENT_TIMESTAMP - INTERVAL '5' DAY + INTERVAL '5' HOUR),
(9006, '00000000-0000-0000-0000-000000000001', 'Admin User', 'REJECT', 'SOURCE', '00000000-0000-0000-0000-000000000310', 'Unknown Blog', '콘텐츠 품질 기준 미달로 반려', CURRENT_TIMESTAMP - INTERVAL '4' DAY),
-- 사용자 계정 승인/반려
(9007, '00000000-0000-0000-0000-000000000001', 'Admin User', 'APPROVE', 'USER_ACCOUNT', 'user-2024-0312', 'John Doe', '가입 승인 — 직무: 개발자', CURRENT_TIMESTAMP - INTERVAL '4' DAY + INTERVAL '1' HOUR),
(9008, '00000000-0000-0000-0000-000000000001', 'Operator User', 'APPROVE', 'USER_ACCOUNT', 'user-2024-0315', 'Jane Doe', '가입 승인 — 직무: 마케터', CURRENT_TIMESTAMP - INTERVAL '3' DAY),
(9009, '00000000-0000-0000-0000-000000000001', 'Admin User', 'REJECT', 'USER_ACCOUNT', 'user-2024-0318', 'Test User', '중복 가입 시도로 반려', CURRENT_TIMESTAMP - INTERVAL '3' DAY + INTERVAL '2' HOUR),
-- 런타임 설정 변경
(9010, '00000000-0000-0000-0000-000000000001', 'Admin User', 'UPDATE', 'RUNTIME_SETTING', 'ai.model', 'AI 모델 설정', '모델 변경: gpt-4o-mini → gpt-4.1-mini', CURRENT_TIMESTAMP - INTERVAL '3' DAY + INTERVAL '4' HOUR),
(9011, '00000000-0000-0000-0000-000000000001', 'Operator User', 'UPDATE', 'RUNTIME_SETTING', 'cost.daily_limit', '일일 비용 한도', '한도 변경: $5.00 → $8.00', CURRENT_TIMESTAMP - INTERVAL '2' DAY),
-- 리뷰 큐 결정
(9012, '00000000-0000-0000-0000-000000000001', 'Admin User', 'APPROVE', 'REVIEW_ITEM', '00000000-0000-0000-0000-000000000601', 'GPT-5 출시 소식', '중요도 높음 — 즉시 발송 승인', CURRENT_TIMESTAMP - INTERVAL '2' DAY + INTERVAL '3' HOUR),
(9013, '00000000-0000-0000-0000-000000000001', 'Operator User', 'REJECT', 'REVIEW_ITEM', '00000000-0000-0000-0000-000000000603', '광고성 콘텐츠 검출', '스팸 패턴 감지 — 발송 거부', CURRENT_TIMESTAMP - INTERVAL '1' DAY),
-- 파이프라인 트리거
(9014, '00000000-0000-0000-0000-000000000001', 'Admin User', 'TRIGGER', 'PIPELINE', 'pipeline-rss-collect', 'RSS 수집 파이프라인', '수동 즉시 실행 — 대상 카테고리: AI/테크', CURRENT_TIMESTAMP - INTERVAL '1' DAY + INTERVAL '2' HOUR),
(9015, '00000000-0000-0000-0000-000000000001', 'Operator User', 'TRIGGER', 'PIPELINE', 'pipeline-delivery-retry', '발송 재시도 파이프라인', '실패 건 일괄 재시도 — 3건 대상', CURRENT_TIMESTAMP - INTERVAL '3' HOUR);

-- ============================================================
-- 10) 발송 관리 (delivery_log) — 최근 7일 발송 이력 12건
-- ============================================================
-- delivery_log uses VARCHAR(36) PK

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002001', '00000000-0000-0000-0000-000000000201', 'C1234567890', CURRENT_DATE - 6, 9, 'SENT', 5, '1710000001.000100', FALSE, CURRENT_TIMESTAMP - INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '6' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002001');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002002', '00000000-0000-0000-0000-000000000202', 'C1234567890', CURRENT_DATE - 6, 9, 'SENT', 3, '1710000002.000200', FALSE, CURRENT_TIMESTAMP - INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '6' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002002');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002003', '00000000-0000-0000-0000-000000000203', 'C1234567890', CURRENT_DATE - 6, 8, 'FAILED', 0, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '6' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002003');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002004', '00000000-0000-0000-0000-000000000205', 'C1234567890', CURRENT_DATE - 6, 9, 'SENT', 4, '1710000004.000400', FALSE, CURRENT_TIMESTAMP - INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '6' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002004');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002005', '00000000-0000-0000-0000-000000000201', 'C1234567890', CURRENT_DATE - 4, 9, 'SENT', 6, '1710200005.000500', FALSE, CURRENT_TIMESTAMP - INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '4' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002005');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002006', '00000000-0000-0000-0000-000000000202', 'C1234567890', CURRENT_DATE - 4, 9, 'FAILED', 0, NULL, TRUE, CURRENT_TIMESTAMP - INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '4' DAY + INTERVAL '30' MINUTE
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002006');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002007', '00000000-0000-0000-0000-000000000203', 'C1234567890', CURRENT_DATE - 4, 8, 'SENT', 4, '1710400007.000700', TRUE, CURRENT_TIMESTAMP - INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '4' DAY + INTERVAL '1' HOUR
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002007');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002008', '00000000-0000-0000-0000-000000000205', 'C1234567890', CURRENT_DATE - 3, 9, 'SENT', 3, '1710500008.000800', FALSE, CURRENT_TIMESTAMP - INTERVAL '3' DAY, CURRENT_TIMESTAMP - INTERVAL '3' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002008');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002009', '00000000-0000-0000-0000-000000000201', 'C1234567890', CURRENT_DATE - 2, 9, 'SENT', 5, '1710600009.000900', FALSE, CURRENT_TIMESTAMP - INTERVAL '2' DAY, CURRENT_TIMESTAMP - INTERVAL '2' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002009');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002010', '00000000-0000-0000-0000-000000000202', 'C1234567890', CURRENT_DATE - 2, 9, 'FAILED', 0, NULL, TRUE, CURRENT_TIMESTAMP - INTERVAL '2' DAY, CURRENT_TIMESTAMP - INTERVAL '2' DAY + INTERVAL '45' MINUTE
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002010');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002011', '00000000-0000-0000-0000-000000000203', 'C1234567890', CURRENT_DATE - 1, 8, 'SENT', 5, '1710700011.001100', FALSE, CURRENT_TIMESTAMP - INTERVAL '1' DAY, CURRENT_TIMESTAMP - INTERVAL '1' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002011');

INSERT INTO delivery_log (id, category_id, channel_id, delivery_date, delivery_hour, status, item_count, slack_message_ts, retry_attempted, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000002012', '00000000-0000-0000-0000-000000000205', 'C1234567890', CURRENT_DATE - 1, 9, 'SENT', 2, '1710700012.001200', FALSE, CURRENT_TIMESTAMP - INTERVAL '1' DAY, CURRENT_TIMESTAMP - INTERVAL '1' DAY
WHERE NOT EXISTS (SELECT 1 FROM delivery_log WHERE id = '00000000-0000-0000-0000-000000002012');

-- ============================================================
-- E2E 테스트 전용 보정 데이터
-- 아래 데이터는 E2E 전체 통과를 위해 필요한 상태를 보장한다.
-- ============================================================

-- 0. departments + teams seed — 회원가입 페이지 소속 드롭다운이 비어있으면
--    가입 버튼이 disabled 상태로 유지돼 E2E 실패. 최소 1개 부서·팀 보장.
INSERT INTO departments (id, name, name_normalized, display_order, is_active, created_at, updated_at)
SELECT '00000000-0000-0000-e001-000000000001', 'AI플랫폼', 'ai플랫폼', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE id = '00000000-0000-0000-e001-000000000001');

INSERT INTO departments (id, name, name_normalized, display_order, is_active, created_at, updated_at)
SELECT '00000000-0000-0000-e001-000000000002', '사업개발', '사업개발', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE id = '00000000-0000-0000-e001-000000000002');

INSERT INTO teams (id, department_id, name, name_normalized, display_order, is_active, created_at, updated_at)
SELECT '00000000-0000-0000-e002-000000000001', '00000000-0000-0000-e001-000000000001', 'AI플랫폼팀', 'ai플랫폼팀', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teams WHERE id = '00000000-0000-0000-e002-000000000001');

INSERT INTO teams (id, department_id, name, name_normalized, display_order, is_active, created_at, updated_at)
SELECT '00000000-0000-0000-e002-000000000002', '00000000-0000-0000-e001-000000000002', '영업팀', '영업팀', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teams WHERE id = '00000000-0000-0000-e002-000000000002');

-- 1. dev.user 구독 한도 여유 확보 (5 → 3)
-- 2개를 WITHDRAWN으로 변경해 위자드 테스트가 동작하도록 한다.
UPDATE clipping_user_requests SET status = 'WITHDRAWN'
WHERE id IN ('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000001007')
  AND status = 'APPROVED';

-- 1b. dev.user 월별 신규 요청 한도 여유 확보 (3 → 1)
-- 월 한도(MAX_MONTHLY_NEW_REQUESTS=3)를 초과하면 위자드 제출이 HTTP 400 으로 실패한다.
-- 1002(APPROVED)·1004(APPROVED) 두 건을 과거 달 날짜로 소급해 이번 달 카운트를 1로 줄인다.
-- 1005(APPROVED)는 CURRENT_TIMESTAMP 유지 → 현재 구독 히스토리 확인용.
UPDATE clipping_user_requests
SET created_at = '2025-01-15 10:00:00', updated_at = '2025-01-15 10:00:00'
WHERE id IN ('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000001004');

-- 2. 리뷰 큐에 REVIEW(대기) 항목 보장
-- 기존 INCLUDE 중 2개를 REVIEW로 되돌려 승인/제외 테스트가 동작하도록 한다.
UPDATE clipping_review_items SET status = 'REVIEW'
WHERE summary_id IN ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000502')
  AND status = 'INCLUDE';

-- 3. PENDING 사용자 계정 보장
-- dev.user.pending은 항상 PENDING 상태를 유지한다.
UPDATE admin_users SET approval_status = 'PENDING', is_active = TRUE
WHERE username = 'dev.user.pending@clipping.local';

-- 4. PENDING 사용자 요청 보장
-- dev.user의 기존 REJECTED 요청 1개를 PENDING으로 되돌려 관리자 요청 승인/반려 테스트가 동작하도록 한다.
UPDATE clipping_user_requests SET status = 'PENDING', reviewed_by_user_id = NULL, reviewed_at = NULL
WHERE requester_user_id = (SELECT id FROM admin_users WHERE username = 'dev.user@clipping.local')
  AND status = 'REJECTED'
  AND id = (
    SELECT id FROM clipping_user_requests
    WHERE requester_user_id = (SELECT id FROM admin_users WHERE username = 'dev.user@clipping.local')
      AND status = 'REJECTED'
    LIMIT 1
  );
