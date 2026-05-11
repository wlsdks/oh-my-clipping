ALTER TABLE admin_users
    ADD COLUMN department VARCHAR(100);

ALTER TABLE admin_users
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

ALTER TABLE admin_users
    ADD COLUMN approval_note TEXT;

ALTER TABLE admin_users
    ADD COLUMN approved_by_user_id VARCHAR(36) REFERENCES admin_users(id);

ALTER TABLE admin_users
    ADD COLUMN approved_at TIMESTAMP;

ALTER TABLE admin_users
    ADD CONSTRAINT chk_admin_users_approval_status
        CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));

CREATE INDEX idx_admin_users_approval_status ON admin_users(approval_status, role, created_at DESC);
