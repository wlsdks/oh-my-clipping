package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.dto.KeywordFrequency
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.KeywordTrendService
import com.ohmyclipping.service.port.ClippingQueryPort
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 사용자 대상 MCP 도구 — 카테고리 개요 통계 및 키워드 트렌드.
 *
 * 대시보드/분석 뷰에서 사용할 집계형 데이터를 반환한다.
 */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class UserInsightTools(
    private val categoryService: CategoryService,
    private val clippingQueryPort: ClippingQueryPort,
    private val keywordTrendService: KeywordTrendService,
) {

    @Tool(
        description = """
            단일 카테고리의 대시보드용 개요 지표를 반환한다.
            **언제 쓰나:** 사용자가 "이 카테고리 어때?", "상태 보여줘", "요약 지표" 처럼 카테고리 단위 요약 상태를 물어볼 때.
            **쓰지 말 것:** 사용자가 기사 목록을 원하는 경우 — user_list_recent_summaries 를 사용.
                             사용자가 키워드를 물어보는 경우 — user_get_trending_keywords 를 사용.
            **파라미터:** category = 카테고리 ID 또는 이름.
            **반환:** sourceCount, subscriberCount, recentItemCount7Days, avgImportance7Days, lastUpdatedAt 가 담긴 CategoryOverview.
        """,
    )
    fun user_get_category_overview(
        @ToolParam(description = "카테고리 ID 또는 이름") category: String,
    ): String = mcpToolCall {
        val cat = categoryService.resolveCategory(category)
        clippingQueryPort.getCategoryOverview(cat.id)
    }

    @Tool(
        description = """
            최근 N일 동안 많이 등장한 상위 키워드를 빈도수·증감률과 함께 반환한다.
            **언제 쓰나:** 사용자가 "요즘 뜨는 키워드", "트렌드", "what's hot" 을 묻거나 키워드 클라우드를 원할 때.
            **쓰지 말 것:** 사용자가 기사 요약을 원하는 경우 — user_search_summaries 또는 user_list_top_summaries 를 사용.
            **파라미터:** category 선택 (생략 시 전체 카테고리 교차), days (1~30, 기본 7), limit (1~50, 기본 15).
            **반환:** 빈도수 내림차순으로 정렬된 KeywordFrequency 리스트.
        """,
    )
    fun user_get_trending_keywords(
        @ToolParam(description = "카테고리 ID 또는 이름 (선택)", required = false) category: String? = null,
        @ToolParam(description = "조회 기간 일수 (1~30, 기본 7)", required = false) days: Int = 7,
        @ToolParam(description = "최대 키워드 수 (1~50, 기본 15)", required = false) limit: Int = 15,
    ): String = mcpToolCall {
        validateDays(days)
        validateLimit(limit)
        // 카테고리 지정이 있으면 ID로 해석하고, 공백뿐이면 전체 카테고리 조회로 둔다.
        val categoryId = category
            ?.takeIf { it.isNotBlank() }
            ?.let { categoryService.resolveCategory(it).id }
        val response = keywordTrendService.getKeywordTrend(
            days = days,
            top = limit,
            categoryId = categoryId,
        )
        response.keywords.map { item ->
            KeywordFrequency(
                keyword = item.keyword,
                count = item.totalCount,
                changeRate = item.changeRate,
            )
        }
    }

    // -- private helpers --

    private fun validateDays(days: Int) {
        if (days < 1 || days > 30) {
            throw InvalidInputException("days must be between 1 and 30")
        }
    }

    private fun validateLimit(limit: Int) {
        if (limit < 1 || limit > 50) {
            throw InvalidInputException("limit must be between 1 and 50")
        }
    }
}
