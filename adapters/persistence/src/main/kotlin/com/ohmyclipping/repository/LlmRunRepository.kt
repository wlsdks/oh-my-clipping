package com.ohmyclipping.repository

import com.ohmyclipping.entity.LlmRunEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface LlmRunRepository : JpaRepository<LlmRunEntity, String> {
    fun findByCreatedAtBetween(from: Instant, to: Instant): List<LlmRunEntity>
    fun findByCreatedAtBetweenAndCategoryId(from: Instant, to: Instant, categoryId: String): List<LlmRunEntity>
    fun deleteByCreatedAtBefore(cutoff: Instant): Int

    @Query(
        """SELECT COALESCE(SUM(e.inputChars), 0), COALESCE(SUM(e.outputChars), 0)
           FROM LlmRunEntity e
           WHERE e.createdAt BETWEEN :from AND :to"""
    )
    fun sumCharsBetween(from: Instant, to: Instant): Array<Any>
}
