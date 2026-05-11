package com.ohmyclipping.admin

import com.ohmyclipping.service.SentimentTrendService
import com.ohmyclipping.service.dto.SentimentTrendResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 논조(감성) 추이 조회 API를 제공하는 컨트롤러.
 * 일별 긍정/중립/부정 기사 건수를 집계하여 반환한다.
 */
@RestController
@RequestMapping("/api/admin/sentiment")
class SentimentTrendAdminController(
    private val sentimentTrendService: SentimentTrendService
) {

    /**
     * 논조 추이를 조회한다.
     *
     * @param days 조회 기간 일수 (기본 7)
     * @param categoryId 카테고리 ID (null이면 전체)
     */
    @GetMapping("/trend")
    fun getTrend(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(required = false) categoryId: String?,
    ): SentimentTrendResponse = sentimentTrendService.getSentimentTrend(days, categoryId)
}
