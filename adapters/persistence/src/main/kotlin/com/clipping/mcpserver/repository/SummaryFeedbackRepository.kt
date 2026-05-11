package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.SummaryFeedbackEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SummaryFeedbackRepository : JpaRepository<SummaryFeedbackEntity, String> {
    fun findBySummaryIdAndUserId(summaryId: String, userId: String): SummaryFeedbackEntity?

    /** 사용자의 피드백을 최신순으로 조회. export limit 은 Pageable 로 제어. */
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): List<SummaryFeedbackEntity>
}
