package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.DailySummaryEntity
import com.clipping.mcpserver.model.DailySummary
import com.clipping.mcpserver.repository.DailySummaryRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 일간 요약 JPA 구현. JdbcDailySummaryStore를 대체한다.
 */
@Repository
@Primary
class JpaDailySummaryStore(
    private val repository: DailySummaryRepository
) : DailySummaryStore {

    private val mapper = jacksonObjectMapper()

    override fun findByCategoryAndDate(categoryId: String, date: LocalDate): DailySummary? =
        repository.findByCategoryIdAndSummaryDate(categoryId, date)?.toModel()

    override fun findByCategoryId(categoryId: String): List<DailySummary> =
        repository.findByCategoryId(categoryId)
            .map { it.toModel() }
            .sortedByDescending { it.summaryDate }

    override fun findByDate(date: LocalDate): List<DailySummary> =
        repository.findBySummaryDate(date)
            .map { it.toModel() }
            .sortedBy { it.categoryId }

    override fun countOlderThan(cutoffDate: LocalDate, categoryId: String?): Int =
        if (categoryId != null) {
            repository.countBySummaryDateBeforeAndCategoryId(cutoffDate, categoryId)
        } else {
            repository.countBySummaryDateBefore(cutoffDate)
        }

    @Transactional
    override fun deleteOlderThan(cutoffDate: LocalDate, categoryId: String?): Int =
        if (categoryId != null) {
            repository.deleteBySummaryDateBeforeAndCategoryId(cutoffDate, categoryId)
        } else {
            repository.deleteBySummaryDateBefore(cutoffDate)
        }

    override fun save(summary: DailySummary): DailySummary {
        val id = summary.id.ifBlank { UUID.randomUUID().toString() }
        val now = Instant.now()
        val entity = DailySummaryEntity(
            id = id,
            title = summary.title,
            totalItems = summary.totalItems,
            summaryDate = summary.summaryDate,
            topicKeywords = mapper.writeValueAsString(summary.topicKeywords),
            overallSummary = summary.overallSummary,
            isSentToSlack = summary.isSentToSlack,
            categoryId = summary.categoryId,
            createdAt = now
        )
        return repository.save(entity).toModel()
    }

    private fun DailySummaryEntity.toModel(): DailySummary {
        val rawTopicKeywords = topicKeywords
        val keywords: List<String> = if (rawTopicKeywords.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { mapper.readValue<List<String>>(rawTopicKeywords) }.getOrDefault(emptyList())
        }
        return DailySummary(
            id = id,
            title = title,
            totalItems = totalItems,
            summaryDate = summaryDate,
            topicKeywords = keywords,
            overallSummary = overallSummary,
            isSentToSlack = isSentToSlack,
            categoryId = categoryId,
            createdAt = createdAt
        )
    }
}
