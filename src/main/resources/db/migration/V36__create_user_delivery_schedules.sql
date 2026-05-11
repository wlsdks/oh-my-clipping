CREATE TABLE user_delivery_schedules (
    user_id       VARCHAR(36) PRIMARY KEY REFERENCES admin_users(id),
    delivery_days VARCHAR(50) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    delivery_hour INT         NOT NULL DEFAULT 9,
    preset        VARCHAR(20) NOT NULL DEFAULT 'WEEKDAYS',
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
