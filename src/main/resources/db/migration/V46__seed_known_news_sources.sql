-- Known news source seed (generic public feeds).
-- region: DOMESTIC / GLOBAL. Add more sources via the admin UI.

INSERT INTO known_news_sources (id, name, aliases, domain, rss_url, region) VALUES

-- ===== Global =====
('a1b2c3d4-3001-4000-8000-000000000001', 'BBC News',
 '["bbc","BBC","BBC News","British Broadcasting"]',
 'bbc.co.uk',
 'https://feeds.bbci.co.uk/news/rss.xml',
 'GLOBAL'),

('a1b2c3d4-3002-4000-8000-000000000002', 'Reuters',
 '["reuters","Reuters","Reuters News"]',
 'reuters.com',
 'https://feeds.reuters.com/reuters/topNews',
 'GLOBAL'),

('a1b2c3d4-3003-4000-8000-000000000003', 'NPR',
 '["npr","NPR","National Public Radio"]',
 'npr.org',
 'https://feeds.npr.org/1001/rss.xml',
 'GLOBAL'),

('a1b2c3d4-3004-4000-8000-000000000004', 'The Guardian',
 '["guardian","Guardian","The Guardian"]',
 'theguardian.com',
 'https://www.theguardian.com/world/rss',
 'GLOBAL'),

-- ===== Technology =====
('a1b2c3d4-4001-4000-8000-000000000005', 'TechCrunch',
 '["techcrunch","TechCrunch","TC"]',
 'techcrunch.com',
 'https://techcrunch.com/feed/',
 'GLOBAL'),

('a1b2c3d4-4002-4000-8000-000000000006', 'Hacker News',
 '["hn","HN","Hacker News","Y Combinator"]',
 'news.ycombinator.com',
 'https://news.ycombinator.com/rss',
 'GLOBAL'),

('a1b2c3d4-4003-4000-8000-000000000007', 'Wired',
 '["wired","Wired","Wired Magazine"]',
 'wired.com',
 'https://www.wired.com/feed/rss',
 'GLOBAL'),

('a1b2c3d4-4004-4000-8000-000000000008', 'The Verge',
 '["verge","The Verge"]',
 'theverge.com',
 'https://www.theverge.com/rss/index.xml',
 'GLOBAL'),

-- ===== Business / Finance =====
('a1b2c3d4-5001-4000-8000-000000000009', 'Financial Times',
 '["ft","FT","Financial Times"]',
 'ft.com',
 'https://www.ft.com/rss/home',
 'GLOBAL'),

('a1b2c3d4-5002-4000-8000-000000000010', 'Bloomberg',
 '["bloomberg","Bloomberg","Bloomberg News"]',
 'bloomberg.com',
 'https://feeds.bloomberg.com/markets/news.rss',
 'GLOBAL')
ON CONFLICT DO NOTHING;
