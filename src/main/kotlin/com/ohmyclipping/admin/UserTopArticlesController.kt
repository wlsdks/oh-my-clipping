package com.ohmyclipping.admin

import com.ohmyclipping.service.TopArticlesService
import com.ohmyclipping.service.dto.analytics.TopArticlesResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 사용자용 주요 기사 조회 API.
 * 일반 사용자(ROLE_USER)가 중요도 기준 상위 기사를 조회할 수 있다.
 */
@RestController
@RequestMapping("/api/user/articles")
class UserTopArticlesController(
    private val topArticlesService: TopArticlesService
) {

    /**
     * 중요도 기준 상위 기사를 조회한다.
     * from/to 파라미터로 월 단위 정확한 기간을 지정할 수 있다.
     */
    @GetMapping("/top")
    fun getTopArticles(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) sentiment: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) date: String?
    ): TopArticlesResponse {
        if (from != null && to != null) {
            // from/to가 지정되면 요청한 고정 날짜 범위를 그대로 사용한다.
            return topArticlesService.getTopArticlesByRange(
                fromDate = LocalDate.parse(from),
                toDate = LocalDate.parse(to),
                limit = limit,
                categoryId = categoryId,
                sentiment = sentiment,
                eventType = eventType,
                keyword = keyword,
                date = date
            )
        }
        return topArticlesService.getTopArticles(
            days = days,
            limit = limit,
            categoryId = categoryId,
            sentiment = sentiment,
            eventType = eventType,
            keyword = keyword,
            date = date
        )
    }
}
