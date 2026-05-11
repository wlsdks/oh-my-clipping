package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.UserMonthlyStatResponse
import com.ohmyclipping.service.UserStatsReportService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.YearMonth

/**
 * 사용자 포털 통계 조회 및 보고서 다운로드 API.
 */
@RestController
@RequestMapping("/api/user")
class UserStatsReportController(
    private val userStatsReportService: UserStatsReportService
) {

    @GetMapping("/stats/monthly")
    fun getMonthlyStats(
        authentication: Authentication,
        @RequestParam yearMonth: String
    ): List<UserMonthlyStatResponse> {
        val ym = YearMonth.parse(yearMonth)
        return userStatsReportService.getOwnMonthlyStats(authentication.name, ym).map { row ->
            UserMonthlyStatResponse(
                id = row.id,
                categoryId = row.categoryId,
                categoryName = row.categoryName,
                statDate = row.statDate.toString(),
                itemsCollected = row.itemsCollected,
                itemsSummarized = row.itemsSummarized,
                itemsSent = row.itemsSent,
                topKeywords = row.topKeywords,
                avgImportanceScore = row.avgImportanceScore
            )
        }
    }

    @GetMapping("/reports/monthly.csv")
    fun downloadMonthlyReport(
        authentication: Authentication,
        @RequestParam yearMonth: String
    ): ResponseEntity<ByteArray> {
        val ym = YearMonth.parse(yearMonth)
        val csv = userStatsReportService.exportOwnMonthlyStatsCsv(authentication.name, ym)
        val fileName = "clipping-user-stats-$ym.csv"

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(csv)
    }
}
