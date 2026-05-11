-- 예산 설정 테이블 (단일 행, id='default')
CREATE TABLE IF NOT EXISTS cost_budget_settings (
    id VARCHAR(36) PRIMARY KEY,
    monthly_budget_usd DOUBLE PRECISION NOT NULL DEFAULT 0,
    alert_threshold_percent INT NOT NULL DEFAULT 80,
    slack_alert_enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO cost_budget_settings (id) VALUES ('default');

-- 날짜 범위 단독 조회 최적화
CREATE INDEX IF NOT EXISTS idx_llm_runs_created ON llm_runs(created_at);

-- 모델별 집계 쿼리 최적화
CREATE INDEX IF NOT EXISTS idx_llm_runs_model_created ON llm_runs(model, created_at);

-- 상태별 집계 쿼리 최적화
CREATE INDEX IF NOT EXISTS idx_llm_runs_status_created ON llm_runs(status, created_at);
