-- 사용자 클리핑 요청에 WITHDRAWN(철회) 상태를 추가한다.
ALTER TABLE clipping_user_requests
    DROP CONSTRAINT IF EXISTS chk_clipping_user_requests_status;

ALTER TABLE clipping_user_requests
    ADD CONSTRAINT chk_clipping_user_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'));
