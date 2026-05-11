-- 페르소나 프리셋 지원을 위한 컬럼 추가
ALTER TABLE clipping_personas ADD COLUMN is_preset BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE clipping_personas ADD COLUMN preview_title TEXT;
ALTER TABLE clipping_personas ADD COLUMN preview_source TEXT;
ALTER TABLE clipping_personas ADD COLUMN preview_body TEXT;

-- 기본 프리셋 4종 삽입
INSERT INTO clipping_personas (id, name, description, system_prompt, summary_style, target_audience, max_items, language, is_active, is_preset, preview_title, preview_source, preview_body, created_at, updated_at)
VALUES
(
    '00000000-0000-0000-0000-000000000001',
    '경영진 브리핑',
    '핵심만 짧게, 의사결정 중심',
    '당신은 경영진용 뉴스 브리핑 작성자입니다.
각 기사를 아래 형식으로 요약하세요.
1) 핵심 내용 2줄 이내
2) 우리 비즈니스에 미치는 영향 1줄
3) 필요한 액션이 있다면 1줄
전문 용어를 쓰되 간결하게 작성하세요.',
    '핵심 2줄 + 비즈니스 임팩트 1줄',
    '경영진·임원',
    5, 'ko', TRUE, TRUE,
    'MegaCorp, 2nm GAA 공정 양산 개시… 파운드리 경쟁 본격화',
    'Example Business Daily · 2분 전',
    '📌 MegaCorp가 2nm GAA 공정 양산을 시작하며 TSMC와의 파운드리 직접 경쟁 구도에 진입했습니다.

💡 비즈니스 영향: 반도체 장비·소재 납품 일정 및 단가 협상에 직접적인 영향.

✅ 파운드리 고객사 다변화 전략을 검토해 주세요.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000002',
    '쉬운 요약',
    '누구나 이해할 수 있는 설명',
    '당신은 뉴스 요약 도우미입니다.
각 기사마다 아래 형식으로 작성하세요.
1) 핵심 내용 3줄
2) 왜 중요한지 1~2줄
3) 지금 할 수 있는 행동 1줄
어려운 용어는 쉬운 말로 바꿔주세요.',
    '핵심 3줄 + 의미 1줄 + 행동 1줄',
    '일반 실무자',
    5, 'ko', TRUE, TRUE,
    'MegaCorp, 2nm GAA 공정 양산 개시… 파운드리 경쟁 본격화',
    'Example Business Daily · 2분 전',
    '📰 MegaCorp가 새로운 반도체 기술(2nm)을 적용한 칩 생산을 시작했어요.
더 작고 빠른 칩을 만드는 기술인데, 세계 1위 업체(TSMC)와 본격적으로 경쟁하게 됩니다.

🤔 왜 중요해요? 이 경쟁 결과가 우리가 쓰는 기기 가격과 성능에도 영향을 줘요.

✅ 반도체·테크 관련 뉴스를 좀 더 주의 깊게 볼 타이밍이에요.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000003',
    '깊이 있는 분석',
    '배경과 맥락까지 자세하게',
    '당신은 심층 뉴스 분석가입니다.
각 기사를 아래 형식으로 자세히 분석하세요.
1) 이 뉴스가 나오게 된 배경 2줄
2) 핵심 내용 2~3줄
3) 관련 업계·시장에 미치는 영향 2줄
4) 앞으로의 전망 1줄
전문 용어는 괄호 안에 쉬운 설명을 덧붙이세요.',
    '배경 2줄 + 핵심 내용 3줄 + 영향 2줄 + 전망 1줄',
    '심층 분석이 필요한 분',
    5, 'ko', TRUE, TRUE,
    'MegaCorp, 2nm GAA 공정 양산 개시… 파운드리 경쟁 본격화',
    'Example Business Daily · 2분 전',
    '📖 배경: 반도체 미세공정은 전력 효율과 성능을 높이는 기술 경쟁입니다. TSMC가 선점한 시장에 MegaCorp이 도전장을 냈습니다.

📌 핵심: MegaCorp가 2nm GAA 공정 양산을 시작. 전력 소모 25% 절감, 성능 12% 향상이 목표.

💡 영향: 반도체 장비·소재 업체들의 납품 일정과 단가에 연쇄 영향.

🔮 전망: 수율이 핵심 변수이며, 상반기 결과에 따라 시장 판도가 바뀔 수 있습니다.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
    '00000000-0000-0000-0000-000000000004',
    '마케팅 인사이트',
    '시장 트렌드와 기회 분석',
    '당신은 마케팅 전문 뉴스 분석가입니다.
각 기사를 아래 형식으로 정리하세요.
1) 소비자·시장 트렌드 2줄
2) 마케팅 시사점 또는 기회 2줄
쉬운 말로 작성하되 인사이트에 집중하세요.',
    '트렌드 요약 2줄 + 마케팅 시사점 2줄',
    '마케터·기획자',
    5, 'ko', TRUE, TRUE,
    'MegaCorp, 2nm GAA 공정 양산 개시… 파운드리 경쟁 본격화',
    'Example Business Daily · 2분 전',
    '📊 반도체 기술 경쟁이 심화되면서, 내년부터 스마트폰·노트북의 성능 향상 속도가 빨라질 전망입니다.

💡 ''차세대 칩 탑재''를 제품 차별화 포인트로 활용할 수 있습니다. AI 기능 강화 디바이스에서 프리미엄 이미지 강화에 효과적일 수 있어요.',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
