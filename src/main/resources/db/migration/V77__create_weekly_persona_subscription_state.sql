-- V77: weekly_persona_subscription_state 테이블 생성.
-- 목적: 주간 페르소나별 유저 구독 상태 명세를 저장한다.
--   - ACTIVE/NEW/CHURNED 상태로 유저의 구독 흐름을 추적한다.
--   - 발송 기회, 실제 발송 수, 클릭/북마크 수를 유저 단위로 집계한다.
--   - weekly_persona_snapshot 집계의 원본 데이터 역할을 한다.
-- 운영 주의사항:
--   - (week_start, persona_id, user_id) 복합 PK 로 중복 삽입을 방지한다.
--   - persona 삭제 시 CASCADE 로 함께 삭제된다.

CREATE TABLE weekly_persona_subscription_state (
    week_start    DATE NOT NULL,
    persona_id    VARCHAR(36) NOT NULL,
    user_id       VARCHAR(36) NOT NULL,
    state         VARCHAR(20) NOT NULL,
    delivery_opportunities INT NOT NULL DEFAULT 0,
    delivered_count        INT NOT NULL DEFAULT 0,
    clicks_in_week         INT NOT NULL DEFAULT 0,
    bookmarks_in_week      INT NOT NULL DEFAULT 0,

    PRIMARY KEY (week_start, persona_id, user_id),
    CONSTRAINT fk_wpss_persona FOREIGN KEY (persona_id)
        REFERENCES clipping_personas(id) ON DELETE CASCADE
);

CREATE INDEX idx_wpss_user_week  ON weekly_persona_subscription_state(user_id, week_start);
CREATE INDEX idx_wpss_week_state ON weekly_persona_subscription_state(week_start, state);
