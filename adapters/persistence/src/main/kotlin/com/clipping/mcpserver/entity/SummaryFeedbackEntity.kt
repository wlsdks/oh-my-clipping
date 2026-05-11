package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 요약 피드백(좋아요/싫어요) 엔티티.
 * summary_feedback 테이블에 매핑된다.
 */
@Entity
@Table(name = "summary_feedback")
class SummaryFeedbackEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "summary_id", length = 36, nullable = false)
    val summaryId: String = "",

    @Column(name = "feedback_type", length = 20, nullable = false)
    var feedbackType: String = "",

    @Column(name = "user_id", length = 120, nullable = false)
    val userId: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
