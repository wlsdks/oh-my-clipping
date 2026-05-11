package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.DailyOperationalKpiResponse
import com.clipping.mcpserver.admin.dto.StatResponse
import com.clipping.mcpserver.service.OperationalKpiService
import com.clipping.mcpserver.service.StatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 통계 조회 API를 제공하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/stats")
class StatsAdminController(
    private val statsService: StatsService,
    private val operationalKpiService: OperationalKpiService
) {

    /**
     * 월별 통계 목록을 조회합니다.
     */
    @GetMapping("/monthly")
    fun getMonthly(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam yearMonth: String
    ): List<StatResponse> {
        val ym = YearMonth.parse(yearMonth)
        return statsService.getMonthlyStats(categoryId, ym).map {
            StatResponse(
                id = it.id,
                categoryId = it.categoryId,
                statDate = it.statDate.toString(),
                itemsCollected = it.itemsCollected,
                itemsDuplicates = it.itemsDuplicates,
                itemsSummarized = it.itemsSummarized,
                itemsSent = it.itemsSent,
                slackSendAttempts = it.slackSendAttempts,
                slackSendSuccesses = it.slackSendSuccesses,
                topKeywords = it.topKeywords,
                avgImportanceScore = it.avgImportanceScore
            )
        }
    }

    /**
     * 운영 KPI를 일 단위로 조회합니다.
     */
    @GetMapping("/daily-kpi")
    fun getDailyKpi(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?
    ): List<DailyOperationalKpiResponse> {
        val toDate = to?.let { LocalDate.parse(it) } ?: LocalDate.now(ZoneId.of("Asia/Seoul"))
        val fromDate = from?.let { LocalDate.parse(it) } ?: toDate.minusDays(13)
        return operationalKpiService.getDailyKpis(categoryId, fromDate, toDate).map {
            DailyOperationalKpiResponse(
                statDate = it.statDate.toString(),
                categoryId = it.categoryId,
                itemsCollected = it.itemsCollected,
                excludedCount = it.excludedCount,
                itemsDuplicates = it.itemsDuplicates,
                noiseRate = it.noiseRate,
                duplicateRate = it.duplicateRate,
                reviewLeadTimeHours = it.reviewLeadTimeHours,
                llmEstimatedCostUsd = it.llmEstimatedCostUsd,
                sendAttempts = it.sendAttempts,
                sendSuccesses = it.sendSuccesses,
                sendSuccessRate = it.sendSuccessRate
            )
        }
    }
}
