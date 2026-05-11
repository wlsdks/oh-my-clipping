package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.dto.clipping.DailySummaryResult
import com.clipping.mcpserver.service.dto.clipping.ExportResult
import com.clipping.mcpserver.service.dto.clipping.OriginalContentResult
import com.clipping.mcpserver.service.dto.clipping.SummaryDetailResult
import com.clipping.mcpserver.service.dto.clipping.SummaryInfo
import com.clipping.mcpserver.service.dto.clipping.SummaryListResult
import com.clipping.mcpserver.service.dto.CategoryOverview
import com.clipping.mcpserver.service.port.ClippingQueryPort
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ClippingQueryAdapter(
    private val clippingService: ClippingService
) : ClippingQueryPort {

    override fun generateDailySummary(categoryId: String): DailySummaryResult =
        clippingService.generateDailySummary(categoryId)

    override fun exportSummaries(
        categoryId: String,
        daysBack: Int?,
        includeOriginal: Boolean?,
        limit: Int?,
    ): ExportResult =
        clippingService.exportSummaries(categoryId, daysBack, includeOriginal, limit)

    override fun listRecentAcrossCategories(sinceDays: Int, limit: Int): SummaryListResult =
        clippingService.listRecentAcrossCategories(sinceDays, limit)

    override fun listRecentForCategory(categoryId: String, sinceDays: Int, limit: Int): SummaryListResult =
        clippingService.listRecentForCategory(categoryId, sinceDays, limit)

    override fun listRecentForCategories(
        categoryIds: List<String>,
        sinceDays: Int,
        limitPerCategory: Int,
    ): Map<String, List<SummaryInfo>> =
        clippingService.listRecentForCategories(categoryIds, sinceDays, limitPerCategory)

    override fun searchSummaries(
        categoryId: String?,
        query: String,
        fromDate: LocalDate?,
        toDate: LocalDate?,
        limit: Int,
    ): SummaryListResult =
        clippingService.searchSummaries(categoryId, query, fromDate, toDate, limit)

    override fun getSummaryDetail(summaryId: String): SummaryDetailResult =
        clippingService.getSummaryDetail(summaryId)

    override fun getOriginalContent(sourceLink: String): OriginalContentResult =
        clippingService.getOriginalContent(sourceLink)

    override fun listTopSummaries(
        categoryId: String,
        days: Int,
        minScore: Double,
        limit: Int,
    ): SummaryListResult =
        clippingService.listTopSummaries(categoryId, days, minScore, limit)

    override fun getCategoryOverview(categoryId: String): CategoryOverview =
        clippingService.getCategoryOverview(categoryId)
}
