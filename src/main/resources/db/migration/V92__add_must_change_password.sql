-- 계정 복구 기능을 위한 비밀번호 변경 필수 플래그 추가
ALTER TABLE admin_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
