-- 리포트 발송 이력을 관리자 UI에서 볼 수 있도록 메타데이터 컬럼을 확장한다.
-- duration_ms: 리포트 생성 + 발송까지 걸린 시간(ms). 운영 병목 진단에 사용한다.
-- items_processed: 리포트에 포함된 아이템/기사 수. 리포트 품질 추적에 사용한다.
-- 기존 error_message 컬럼은 그대로 두어 실패 상세를 유지한다.
--
-- H2/PostgreSQL 양쪽 호환을 위해 ALTER TABLE 당 한 컬럼씩 분리한다.
-- (H2는 한 ALTER에 여러 ADD COLUMN IF NOT EXISTS 를 붙이면 문법 오류로 거부한다)
ALTER TABLE report_delivery_log ADD COLUMN IF NOT EXISTS duration_ms BIGINT;
ALTER TABLE report_delivery_log ADD COLUMN IF NOT EXISTS items_processed INTEGER;

-- 관리자 UI에서 최신순 조회를 빠르게 하기 위한 복합 인덱스.
-- 단건 스케줄 조회(필터: report_type)의 최신순 ORDER BY 에 활용된다.
CREATE INDEX IF NOT EXISTS idx_report_delivery_log_type_updated
    ON report_delivery_log(report_type, updated_at DESC);
