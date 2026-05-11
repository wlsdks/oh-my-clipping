package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ExcludedItemRow
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import com.clipping.mcpserver.store.SummaryCategoryCountStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 카테고리별 키워드 규칙 통계를 제공한다.
 * 포함/검토/제외 건수 집계와 제외 항목 상세 조회를 담당한다.
 */
@Service
class CategoryRuleStatsService(
    private val categoryStore: CategoryStore,
    private val categoryRuleStore: CategoryRuleStore,
    private val summaryCategoryCountStore: SummaryCategoryCountStore,
    private val reviewItemDecisionStore: ReviewItemDecisionStore
) {

    /**
     * 최근 N일간의 카테고리별 분류 통계를 집계한다.
     * INCLUDE 건수는 전체 기사 수에서 REVIEW/EXCLUDE를 빼서 산출한다.
     */
    fun getRuleStats(days: Int): RuleStatsResult {
        val safeDays = days.coerceIn(1, 90)
        val now = Instant.now()
        val from = now.minus(safeDays.toLong(), ChronoUnit.DAYS)

        // 카테고리별 기사 총 건수를 조회한다.
        val totalsByCategory = summaryCategoryCountStore.countByCategory(from, now)

        // 카테고리별·상태별 리뷰 건수를 조회한다.
        val statusCounts = reviewItemDecisionStore.countByStatusGroupedByCategory(from, now)
        val statusMap = statusCounts.groupBy { it.categoryId }

        // 카테고리 목록과 규칙 정보를 조회한다.
        val categories = categoryStore.list()

        var totalIncluded = 0
        var totalReview = 0
        var totalExcluded = 0

        val perCategory = categories.map { cat ->
            val total = totalsByCategory[cat.id] ?: 0
            val statuses = statusMap[cat.id] ?: emptyList()
            val review = statuses
                .filter { it.status == ReviewDecisionStatus.REVIEW.name }
                .sumOf { it.count }
            val excluded = statuses
                .filter { it.status == ReviewDecisionStatus.EXCLUDE.name }
                .sumOf { it.count }
            // 포함 건수 = 전체 - 검토 - 제외
            val included = (total - review - excluded).coerceAtLeast(0)

            totalIncluded += included
            totalReview += review
            totalExcluded += excluded

            // 규칙 존재 여부 확인
            val rule = categoryRuleStore.findByCategoryId(cat.id)
            val hasRule = rule != null &&
                (rule.includeKeywords.isNotEmpty() || rule.excludeKeywords.isNotEmpty())

            CategoryRuleStatItem(
                categoryId = cat.id,
                categoryName = cat.name,
                included = included,
                review = review,
                excluded = excluded,
                hasRule = hasRule
            )
        }

        return RuleStatsResult(
            totalIncluded = totalIncluded,
            totalReview = totalReview,
            totalExcluded = totalExcluded,
            perCategory = perCategory
        )
    }

    /**
     * 특정 카테고리의 제외 항목을 조회한다.
     * reason 문자열에서 매칭 키워드를 추출한다.
     */
    fun getExcludedItems(categoryId: String, limit: Int): ExcludedItemsResult {
        val safeLimit = limit.coerceIn(1, 50)
        val items = reviewItemDecisionStore.findExcludedItems(categoryId, safeLimit)

        return ExcludedItemsResult(
            total = items.size,
            items = items.map { row ->
                ExcludedItemDetail(
                    title = row.title,
                    reason = row.reason ?: "",
                    matchedKeyword = parseMatchedKeyword(row.reason),
                    score = row.score.toDouble(),
                    excludedAt = row.excludedAt.toString()
                )
            }
        )
    }

    /** reason 문자열에서 제외 키워드를 추출한다. */
    private fun parseMatchedKeyword(reason: String?): String? {
        if (reason.isNullOrBlank()) return null
        val regex = Regex("제외 키워드 일치: (.+)")
        return regex.find(reason)?.groupValues?.getOrNull(1)?.trim()
    }
}

/** 규칙 통계 전체 결과. */
data class RuleStatsResult(
    val totalIncluded: Int,
    val totalReview: Int,
    val totalExcluded: Int,
    val perCategory: List<CategoryRuleStatItem>
)

/** 카테고리별 통계 항목. */
data class CategoryRuleStatItem(
    val categoryId: String,
    val categoryName: String,
    val included: Int,
    val review: Int,
    val excluded: Int,
    val hasRule: Boolean
)

/** 제외 항목 조회 결과. */
data class ExcludedItemsResult(
    val total: Int,
    val items: List<ExcludedItemDetail>
)

/** 제외 항목 상세. */
data class ExcludedItemDetail(
    val title: String,
    val reason: String,
    val matchedKeyword: String?,
    val score: Double,
    val excludedAt: String
)
