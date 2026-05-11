-- V56__create_pipeline_runs.sql
-- 파이프라인 실행 이력과 단계별 추적 테이블

CREATE TABLE pipeline_runs (
    id VARCHAR(36) PRIMARY KEY,
    category_id VARCHAR(36) NOT NULL REFERENCES batch_categories(id),
    category_name VARCHAR(200),
    triggered_by VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    orchestration_mode VARCHAR(20),
    total_collected INT DEFAULT 0,
    total_summarized INT DEFAULT 0,
    total_digest_selected INT DEFAULT 0,
    posted_to_slack BOOLEAN DEFAULT FALSE,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP,
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE pipeline_step_traces (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    step VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    duration_ms BIGINT,
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pipeline_runs_category ON pipeline_runs(category_id);
CREATE INDEX idx_pipeline_runs_started ON pipeline_runs(started_at DESC);
CREATE INDEX idx_pipeline_runs_status ON pipeline_runs(status);
CREATE INDEX idx_pipeline_step_traces_run ON pipeline_step_traces(run_id);
