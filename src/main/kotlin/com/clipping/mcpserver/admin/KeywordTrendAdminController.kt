package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.dto.KeywordTrendResponse
import com.clipping.mcpserver.service.KeywordTrendService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 키워드 트렌드 조회 API를 제공하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/keywords")
class KeywordTrendAdminController(
    private val keywordTrendService: KeywordTrendService
) {

    /**
     * 키워드 트렌드를 조회한다.
     *
     * @param days 조회 기간 일수 (기본 7)
     * @param top 상위 키워드 개수 (기본 10)
     * @param categoryId 카테고리 ID (null이면 전체)
     */
    @GetMapping("/trend")
    fun getKeywordTrend(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") top: Int,
        @RequestParam(required = false) categoryId: String?
    ): KeywordTrendResponse = keywordTrendService.getKeywordTrend(days, top, categoryId)
}
