package com.ohmyclipping.admin

import com.ohmyclipping.service.TopArticlesService
import com.ohmyclipping.service.dto.analytics.TopArticlesResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 중요도 기준 상위 기사 조회 API.
 */
@RestController
@RequestMapping("/api/admin/articles")
class TopArticlesAdminController(private val topArticlesService: TopArticlesService) {

    /**
     * 중요도 기준 상위 기사를 조회한다.
     * sentiment, eventType, keyword, date 필터를 선택적으로 적용할 수 있다.
     */
    @GetMapping("/top")
    fun getTopArticles(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) sentiment: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) date: String?
    ): TopArticlesResponse = topArticlesService.getTopArticles(
        days = days,
        limit = limit,
        categoryId = categoryId,
        sentiment = sentiment,
        eventType = eventType,
        keyword = keyword,
        date = date
    )
}
