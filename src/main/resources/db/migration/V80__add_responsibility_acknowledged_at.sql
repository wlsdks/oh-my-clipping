-- 관리자 소스 추가 시 법적 검토 책임 확인 시각을 기록한다.
ALTER TABLE rss_sources ADD COLUMN IF NOT EXISTS responsibility_acknowledged_at TIMESTAMP;
