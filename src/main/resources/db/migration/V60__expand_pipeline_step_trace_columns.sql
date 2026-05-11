-- 파이프라인 반복 step 이름이 잘리지 않도록 step/status 컬럼 길이를 확장한다.

ALTER TABLE pipeline_step_traces
    ALTER COLUMN step TYPE VARCHAR(80);

ALTER TABLE pipeline_step_traces
    ALTER COLUMN status TYPE VARCHAR(32);
