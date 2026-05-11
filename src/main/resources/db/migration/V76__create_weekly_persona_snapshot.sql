-- V76: weekly_persona_snapshot 테이블 생성.
-- 목적: 페르소나별 주간 집계 스냅샷을 저장한다.
--   - 구독 증감, 발송 횟수, 인게이지먼트 지표(클릭/북마크/참여율)를 한 row 에 집약한다.
--   - 페르소나 분석 Slice 2 의 BatchRunService 가 매주 채워 넣는다.
-- 운영 주의사항:
--   - persona_id 참조의 FK 는 페르소나 삭제 시 SET NULL 처리한다.
--   - week_start + persona_id 복합 유니크 제약으로 중복 집계를 방지한다.

CREATE TABLE weekly_persona_snapshot (
    id                  VARCHAR(36) PRIMARY KEY,
    week_start          DATE NOT NULL,
    persona_id          VARCHAR(36),
    persona_name        VARCHAR(200) NOT NULL,
    is_preset           BOOLEAN NOT NULL,

    active_subs         INT NOT NULL DEFAULT 0,
    new_subs            INT NOT NULL DEFAULT 0,
    churned_subs        INT NOT NULL DEFAULT 0,

    delivered_count     INT NOT NULL DEFAULT 0,
    delivered_items     INT NOT NULL DEFAULT 0,

    engaged_users       INT NOT NULL DEFAULT 0,
    total_clicks        INT NOT NULL DEFAULT 0,
    total_bookmarks     INT NOT NULL DEFAULT 0,

    engagement_rate     NUMERIC(5,4) NOT NULL DEFAULT 0,
    click_per_delivery  NUMERIC(8,4) NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_wps_week_persona UNIQUE (week_start, persona_id),
    CONSTRAINT fk_wps_persona FOREIGN KEY (persona_id)
        REFERENCES clipping_personas(id) ON DELETE SET NULL
);

CREATE INDEX idx_wps_week_start   ON weekly_persona_snapshot(week_start);
CREATE INDEX idx_wps_persona_week ON weekly_persona_snapshot(persona_id, week_start);
CREATE INDEX idx_wps_preset_week  ON weekly_persona_snapshot(is_preset, week_start);
