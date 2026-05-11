-- V75: user_events 테이블에 summary_id 컬럼 추가.
-- (원래 Slice 1 plan 에서는 V74 였으나 main 에 V74 가 이미 존재해 V75 로 시프트.
--  Slice 2~5 의 후속 마이그레이션도 V76~V81 로 한 칸씩 밀린다.)
--
-- 목적:
--   1. article_impression / article_click / bookmark_toggle 이벤트를
--      persona_id 로 역추적 가능하게 한다 (페르소나 분석 Slice 2 의 engagement 집계).
--   2. 기존 event_data JSON 파싱 없이 인덱스 기반 조인 성능을 확보한다.
-- 운영 주의사항:
--   - 기존 데이터 백필은 이 마이그레이션에 포함하지 않는다 (대량 UPDATE 락 경합 방지).
--   - 운영 백필은 docs/RUNBOOK_PERSONA_ANALYTICS.md 의 배치 스크립트로 별도 실행한다.
--   - 신규 이벤트는 UserEventService 가 코드 레벨에서 summary_id 를 직접 채워 넣는다.

ALTER TABLE user_events ADD COLUMN summary_id VARCHAR(36);

CREATE INDEX idx_user_events_summary_id
    ON user_events(summary_id);

CREATE INDEX idx_user_events_type_summary_created
    ON user_events(event_type, summary_id, created_at);
