package com.ohmyclipping.service.port

import com.ohmyclipping.service.dto.clipping.DailySummaryResult
import com.ohmyclipping.service.dto.clipping.ExportResult
import com.ohmyclipping.service.dto.clipping.OriginalContentResult
import com.ohmyclipping.service.dto.clipping.SummaryDetailResult
import com.ohmyclipping.service.dto.clipping.SummaryInfo
import com.ohmyclipping.service.dto.clipping.SummaryListResult
import com.ohmyclipping.service.dto.CategoryOverview
import java.time.LocalDate

/**
 * MCP/user 조회성 use case 가 concrete ClippingService 에 직접 의존하지 않도록 하는 앱 포트.
 */
interface ClippingQueryPort {
    fun generateDailySummary(categoryId: String): DailySummaryResult

    fun exportSummaries(
        categoryId: String,
        daysBack: Int?,
        includeOriginal: Boolean?,
        limit: Int?,
    ): ExportResult

    fun listRecentAcrossCategories(sinceDays: Int, limit: Int): SummaryListResult

    fun listRecentForCategory(categoryId: String, sinceDays: Int, limit: Int): SummaryListResult

    fun listRecentForCategories(
        categoryIds: List<String>,
        sinceDays: Int,
        limitPerCategory: Int,
    ): Map<String, List<SummaryInfo>>

    fun searchSummaries(
        categoryId: String?,
        query: String,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null,
        limit: Int = 10,
    ): SummaryListResult

    fun getSummaryDetail(summaryId: String): SummaryDetailResult

    fun getOriginalContent(sourceLink: String): OriginalContentResult

    fun listTopSummaries(
        categoryId: String,
        days: Int,
        minScore: Double,
        limit: Int,
    ): SummaryListResult

    fun getCategoryOverview(categoryId: String): CategoryOverview
}
