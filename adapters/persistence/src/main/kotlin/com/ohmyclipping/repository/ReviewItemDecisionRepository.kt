package com.ohmyclipping.repository

import com.ohmyclipping.entity.ReviewItemDecisionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ReviewItemDecisionRepository : JpaRepository<ReviewItemDecisionEntity, String> {
    fun findBySummaryId(summaryId: String): ReviewItemDecisionEntity?
    fun findBySummaryIdIn(summaryIds: List<String>): List<ReviewItemDecisionEntity>
    fun findByReviewedAtBetween(from: Instant, to: Instant): List<ReviewItemDecisionEntity>
    fun findByReviewedAtBetweenAndCategoryId(from: Instant, to: Instant, categoryId: String): List<ReviewItemDecisionEntity>
    fun findByCategoryIdAndStatusOrderByReviewedAtDesc(categoryId: String, status: String): List<ReviewItemDecisionEntity>
}
