CREATE TABLE clipping_jobs (
    id VARCHAR(36) PRIMARY KEY,
    job_type VARCHAR(30) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    next_run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    result_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clipping_jobs_status_next_run ON clipping_jobs(status, next_run_at);
CREATE INDEX idx_clipping_jobs_created_at ON clipping_jobs(created_at);
