package com.ohmyclipping.store

import com.ohmyclipping.model.ReviewItemAudit
import java.time.Instant

interface ReviewItemAuditStore {
    /**
     * 특정 요약 항목의 상태 변경 이력을 최신순으로 조회합니다.
     */
    fun listBySummaryId(summaryId: String, limit: Int = 30): List<ReviewItemAudit>

    /**
     * 상태 변경 이력을 신규로 적재합니다.
     */
    fun append(audit: ReviewItemAudit): ReviewItemAudit

    /**
     * 다수의 상태 변경 이력을 한 번에 적재합니다. 빈 목록이면 즉시 반환합니다.
     */
    fun batchAppend(audits: List<ReviewItemAudit>): List<ReviewItemAudit>

    /**
     * `cutoff` 이전에 생성된 감사 이력을 최대 `limit` 건까지 삭제한다.
     *
     * 대량 DELETE가 테이블 락을 장시간 점유하지 않도록 호출 측에서
     * 반복 호출(chunk delete)하는 것을 전제한다. 실제 삭제된 row 수를 반환하며,
     * 0을 반환하면 더 이상 삭제할 대상이 없다는 뜻이다.
     *
     * @param cutoff 이 시각보다 이전(`created_at < cutoff`)인 이력을 대상으로 한다.
     * @param limit 한 번의 호출에서 삭제할 최대 row 수. 1 이상이어야 한다.
     */
    fun deleteOlderThan(cutoff: Instant, limit: Int): Int
}
