-- 파이프라인 대시보드 매트릭스 쿼리 최적화: delivery_date + category_id + status 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_delivery_log_date_category_status
    ON delivery_log(delivery_date, category_id, status);
