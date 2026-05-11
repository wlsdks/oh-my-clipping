CREATE TABLE report_settings (
    id            VARCHAR(36) PRIMARY KEY,
    setting_key   VARCHAR(50) NOT NULL UNIQUE,
    setting_value TEXT        NOT NULL,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO report_settings (id, setting_key, setting_value, updated_at) VALUES
    (gen_random_uuid(), 'weekly_enabled',                'false',   CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'weekly_day',                    'MONDAY',  CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'weekly_hour',                   '9',       CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'weekly_slack_channel_id',       '',        CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'weekly_include_keyword_trend',  'true',    CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'weekly_include_competitor',      'true',    CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'weekly_include_top_articles',   'true',    CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'weekly_include_sentiment',      'false',   CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'monthly_enabled',               'false',   CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'monthly_hour',                  '9',       CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'monthly_slack_channel_id',      '',        CURRENT_TIMESTAMP);
