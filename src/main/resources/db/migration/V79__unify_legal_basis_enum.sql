-- 기존 SourceComplianceModal이 사용하던 구형 값을 백엔드 표준 값으로 통일
-- CHECK 제약조건이 이미 4값만 허용하므로, 제약조건 우회가 필요한 경우에만 적용
UPDATE rss_sources SET legal_basis = 'QUOTATION_ONLY' WHERE legal_basis = 'FAIR_USE';
UPDATE rss_sources SET legal_basis = 'LICENSED' WHERE legal_basis = 'CONTRACT';
UPDATE rss_sources SET legal_basis = 'OPEN_LICENSE' WHERE legal_basis = 'LICENSE';
UPDATE rss_sources SET legal_basis = 'QUOTATION_ONLY' WHERE legal_basis = 'UNKNOWN';
