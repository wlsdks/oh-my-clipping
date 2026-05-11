-- V78: persona_batch_run 테이블 생성.
-- 목적: 페르소나 분석 배치의 실행 이력과 상태를 기록한다.
--   - SCHEDULED/MANUAL/BACKFILL 트리거 유형을 구분한다.
--   - 단계별(snapshot/anomaly/clustering/report) 상태와 전체 상태를 분리 관리한다.
--   - LLM 호출 수, 임베딩 수, 토큰 사용량 등 비용 지표를 함께 기록한다.
-- 운영 주의사항:
--   - run_id 유니크 제약으로 중복 배치 실행을 감지한다.
--   - finished_at, error_message, error_step 은 실패/완료 시에만 채워진다.

CREATE TABLE persona_batch_run (
    id                VARCHAR(36) PRIMARY KEY,
    run_id            VARCHAR(36) NOT NULL,
    trigger_type      VARCHAR(20) NOT NULL,
    week_start        DATE NOT NULL,
    started_at        TIMESTAMP NOT NULL,
    finished_at       TIMESTAMP,
    overall_status    VARCHAR(20) NOT NULL,

    snapshot_status   VARCHAR(20),
    anomaly_status    VARCHAR(20),
    clustering_status VARCHAR(20),
    report_status     VARCHAR(20),

    personas_scanned   INT DEFAULT 0,
    anomalies_created  INT DEFAULT 0,
    anomalies_resolved INT DEFAULT 0,
    embedding_calls    INT DEFAULT 0,
    llm_calls          INT DEFAULT 0,
    llm_tokens_used    INT DEFAULT 0,

    error_message     TEXT,
    error_step        VARCHAR(20),
    triggered_by      VARCHAR(36),

    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_pbr_run_id UNIQUE (run_id)
);

CREATE INDEX idx_pbr_week    ON persona_batch_run(week_start);
CREATE INDEX idx_pbr_started ON persona_batch_run(started_at);
CREATE INDEX idx_pbr_status  ON persona_batch_run(overall_status);
