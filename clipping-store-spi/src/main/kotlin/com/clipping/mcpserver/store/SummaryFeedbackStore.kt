package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.SummaryFeedback
import com.clipping.mcpserver.model.SummaryFeedbackHotSummary
import java.time.Instant

interface SummaryFeedbackStore {
    fun upsert(feedback: SummaryFeedback): SummaryFeedback

    /**
     * 특정 사용자의 특정 요약에 대한 피드백을 삭제한다.
     * MCP `user_toggle_feedback(reaction=NONE)` 에서 사용자의 기존 반응을 해제하기 위해 호출된다.
     * 대상이 존재하지 않아도 예외 없이 false 를 반환한다.
     * @return 실제 삭제 행 수가 1 이상이면 true
     */
    fun deleteBySummaryIdAndUserId(summaryId: String, userId: String): Boolean

    fun findWeeklyHot(
        from: Instant,
        to: Instant,
        limit: Int,
        categoryId: String?
    ): List<SummaryFeedbackHotSummary>

    /**
     * 특정 사용자가 남긴 피드백을 최신순으로 반환한다. 개인정보 export 용.
     *
     * @param userId `admin_users.id`
     * @param limit 반환 상한 (export 크기 보호용)
     */
    fun findByUserId(userId: String, limit: Int): List<SummaryFeedback>
}
