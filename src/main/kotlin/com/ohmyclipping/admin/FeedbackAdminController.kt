package com.ohmyclipping.admin

import com.ohmyclipping.service.dto.clipping.HotFeedbackResult
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.SummaryFeedbackService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자용 피드백 분석 API.
 * 주간 핫 피드백 조회 등 관리자 전용 기능을 담당한다.
 */
@RestController
@RequestMapping("/api/admin/feedback")
class FeedbackAdminController(
    private val summaryFeedbackService: SummaryFeedbackService
) {

    /**
     * 기간 내 주요 피드백 요약을 조회한다.
     *
     * @param categoryId 카테고리 필터 (선택)
     * @param limit 최대 결과 수 (기본 10, 최대 100)
     * @param days 조회 기간 (기본 7일, 최대 365일)
     */
    @GetMapping("/hot")
    fun getWeeklyHotFeedback(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) days: Int?
    ): HotFeedbackResult {
        val safeLimit = limit ?: 10
        val safeDays = days ?: 7
        ensureValid(safeLimit in 1..100) { "limit는 1~100 범위여야 합니다" }
        ensureValid(safeDays in 1..365) { "days는 1~365 범위여야 합니다" }
        return summaryFeedbackService.getWeeklyHotSummary(safeLimit, safeDays, categoryId)
    }
}
