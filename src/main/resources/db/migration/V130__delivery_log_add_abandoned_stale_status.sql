-- delivery_log 체크 제약에 ABANDONED, STALE 상태를 추가한다.
-- DeliveryRetryOrchestrator가 MAX_RETRIES 도달 시 ABANDONED로,
-- 24시간 경과 시 STALE로 전환하는데 기존 제약이 이를 허용하지 않았다.
ALTER TABLE delivery_log DROP CONSTRAINT IF EXISTS chk_delivery_log_status;
ALTER TABLE delivery_log ADD CONSTRAINT chk_delivery_log_status
  CHECK (status IN ('RESERVED', 'SENT', 'SKIPPED', 'FAILED', 'FINALIZATION_FAILED', 'RETRYING', 'ABANDONED', 'STALE'));
