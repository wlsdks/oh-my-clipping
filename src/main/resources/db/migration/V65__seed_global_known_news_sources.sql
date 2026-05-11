-- 해외 주요 뉴스소스 시드 데이터 추가 (Google News RSS 검증 완료 2026-04-03)
-- 기존 GLOBAL 5개 + 신규 9개 = 14개

INSERT INTO known_news_sources (id, name, aliases, domain, rss_url, region) VALUES

-- 해외 — 종합
('a1b2c3d4-5006-4000-8000-000000000031', 'AP News',
 '["AP","에이피","apnews","Associated Press"]',
 'apnews.com',
 'https://apnews.com/index.rss',
 'GLOBAL'),

('a1b2c3d4-5007-4000-8000-000000000032', 'BBC',
 '["BBC","비비시","bbc","BBC News"]',
 'bbc.com',
 'https://feeds.bbci.co.uk/news/rss.xml',
 'GLOBAL'),

('a1b2c3d4-5008-4000-8000-000000000033', 'CNN',
 '["CNN","씨엔엔","cnn","CNN News"]',
 'cnn.com',
 'http://rss.cnn.com/rss/edition.rss',
 'GLOBAL'),

('a1b2c3d4-5009-4000-8000-000000000034', 'The New York Times',
 '["NYT","뉴욕타임스","nytimes","New York Times","뉴욕타임즈"]',
 'nytimes.com',
 'https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml',
 'GLOBAL'),

('a1b2c3d4-5010-4000-8000-000000000035', 'The Guardian',
 '["가디언","theguardian","Guardian","더가디언"]',
 'theguardian.com',
 'https://www.theguardian.com/world/rss',
 'GLOBAL'),

-- 해외 — 경제·비즈
('a1b2c3d4-5011-4000-8000-000000000036', 'Bloomberg',
 '["블룸버그","bloomberg","Bloomberg News"]',
 'bloomberg.com',
 'https://feeds.bloomberg.com/markets/news.rss',
 'GLOBAL'),

('a1b2c3d4-5012-4000-8000-000000000037', 'CNBC',
 '["CNBC","씨엔비씨","cnbc"]',
 'cnbc.com',
 'https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114',
 'GLOBAL'),

('a1b2c3d4-5013-4000-8000-000000000038', 'The Wall Street Journal',
 '["WSJ","월스트리트저널","wsj","Wall Street Journal"]',
 'wsj.com',
 'https://feeds.a.dj.com/rss/RSSWorldNews.xml',
 'GLOBAL'),

('a1b2c3d4-5014-4000-8000-000000000039', 'Forbes',
 '["포브스","forbes","Forbes"]',
 'forbes.com',
 'https://www.forbes.com/innovation/feed2',
 'GLOBAL'),

-- 해외 — 테크
('a1b2c3d4-5015-4000-8000-000000000040', 'Wired',
 '["와이어드","wired","Wired"]',
 'wired.com',
 'https://www.wired.com/feed/rss',
 'GLOBAL'),

('a1b2c3d4-5016-4000-8000-000000000041', 'Axios',
 '["악시오스","axios","Axios"]',
 'axios.com',
 'https://api.axios.com/feed/',
 'GLOBAL');
