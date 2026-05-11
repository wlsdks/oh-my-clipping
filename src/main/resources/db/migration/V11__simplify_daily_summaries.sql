ALTER TABLE daily_summaries ADD COLUMN topic_keywords TEXT;
ALTER TABLE daily_summaries ALTER COLUMN overall_summary DROP NOT NULL;
