-- 과거 요청 승인 시 생성된 suffix 기반 카테고리 이름을 원래 요청명으로 되돌린다.
UPDATE batch_categories
SET name = (
    SELECT req.request_name
    FROM clipping_user_requests req
    WHERE req.status = 'APPROVED'
      AND req.approved_category_id = batch_categories.id
      AND batch_categories.name = CONCAT(SUBSTRING(req.request_name, 1, 108), '-', SUBSTRING(req.id, 1, 8))
)
WHERE EXISTS (
    SELECT 1
    FROM clipping_user_requests req
    WHERE req.status = 'APPROVED'
      AND req.approved_category_id = batch_categories.id
      AND batch_categories.name = CONCAT(SUBSTRING(req.request_name, 1, 108), '-', SUBSTRING(req.id, 1, 8))
);

-- 과거 요청 승인 시 생성된 suffix 기반 페르소나 이름을 원래 페르소나명으로 되돌린다.
UPDATE clipping_personas
SET name = (
    SELECT req.persona_name
    FROM clipping_user_requests req
    WHERE req.status = 'APPROVED'
      AND req.approved_persona_id = clipping_personas.id
      AND clipping_personas.name = CONCAT(SUBSTRING(req.persona_name, 1, 108), '-', SUBSTRING(req.id, 1, 8))
)
WHERE EXISTS (
    SELECT 1
    FROM clipping_user_requests req
    WHERE req.status = 'APPROVED'
      AND req.approved_persona_id = clipping_personas.id
      AND clipping_personas.name = CONCAT(SUBSTRING(req.persona_name, 1, 108), '-', SUBSTRING(req.id, 1, 8))
);

-- 과거 quick setup이 남긴 suffix 기반 요청 표시명도 실제 승인 페르소나명으로 정렬한다.
UPDATE clipping_user_requests
SET persona_name = (
    SELECT persona.name
    FROM clipping_personas persona
    WHERE persona.id = clipping_user_requests.approved_persona_id
)
WHERE status = 'APPROVED'
  AND approved_persona_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM clipping_personas persona
      WHERE persona.id = clipping_user_requests.approved_persona_id
        AND clipping_user_requests.persona_name <> persona.name
        AND clipping_user_requests.persona_name LIKE CONCAT(persona.name, '-________')
  );
