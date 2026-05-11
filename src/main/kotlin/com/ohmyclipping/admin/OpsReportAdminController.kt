package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.QualitySummaryResponse
import com.ohmyclipping.admin.dto.ReportReleaseResponse
import com.ohmyclipping.service.AdminTrendSnapshotService
import com.ohmyclipping.service.QualitySummaryResult
import com.ohmyclipping.service.ReportReleaseResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 운영 리포트 관리자 API.
 * 리포트 릴리즈 목록과 품질 요약을 제공한다.
 */
@RestController
@RequestMapping("/api/admin/ops-reports")
class OpsReportAdminController(
    private val trendSnapshotService: AdminTrendSnapshotService
) {

    @GetMapping("/releases")
    fun listReportReleases(
        @RequestParam(required = false, defaultValue = "30") days: Int,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): List<ReportReleaseResponse> =
        trendSnapshotService.listReportReleases(days, categoryId, limit).map { it.toResponse() }

    @GetMapping("/summary")
    fun getQualitySummary(
        @RequestParam(required = false, defaultValue = "30") days: Int
    ): QualitySummaryResponse =
        trendSnapshotService.qualitySummary(days).toResponse()

    private fun ReportReleaseResult.toResponse() = ReportReleaseResponse(
        summaryId = summaryId,
        categoryId = categoryId,
        title = title,
        sourceLink = sourceLink,
        importanceScore = importanceScore,
        releaseType = releaseType,
        detectionReason = detectionReason,
        createdAt = createdAt.toString()
    )

    private fun QualitySummaryResult.toResponse() = QualitySummaryResponse(
        from = from.toString(),
        to = to.toString(),
        days = days,
        itemsCollected = itemsCollected,
        itemsSummarized = itemsSummarized,
        itemsSent = itemsSent,
        reviewPendingCount = reviewPendingCount,
        reviewPendingRate = reviewPendingRate,
        excludeRate = excludeRate,
        feedbackTotal = feedbackTotal,
        feedbackPositiveRate = feedbackPositiveRate,
        sendSuccessRate = sendSuccessRate,
        recommendations = recommendations
    )
}
