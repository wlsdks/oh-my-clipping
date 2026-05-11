-- keywords → aliases 컬럼 이름 변경 + exclude_keywords 추가
ALTER TABLE competitor_watchlist RENAME COLUMN keywords TO aliases;
ALTER TABLE competitor_watchlist ADD COLUMN exclude_keywords TEXT DEFAULT '[]';
