package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.dto.DtoSanitizer
import com.ohmyclipping.mcp.dto.SummaryView
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.dto.clipping.SummaryInfo
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.port.ClippingQueryPort
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * 사용자 대상 MCP 도구 — 요약 목록 조회/검색/상위 선정.
 *
 * 모든 응답 필드는 [DtoSanitizer]로 프롬프트 인젝션 방어 처리되어 반환된다.
 */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class UserSummaryTools(
    private val categoryService: CategoryService,
    private val clippingQueryPort: ClippingQueryPort,
    private val sanitizer: DtoSanitizer,
) {

    @Tool(
        description = """
        최근 기간 내 생성된 최신 요약 목록을 반환한다. 카테고리를 지정하면 해당 카테고리만,
        생략하면 전체 카테고리에 걸쳐 최신순으로 모아서 반환한다.
        **언제 쓰나:** 사용자가 "뭐 새로 나왔어", "최근 뉴스", "오늘 뉴스 다 보여줘", "이번 주 보여줘" 처럼 키워드 없이 둘러볼 때.
        **쓰지 말 것:** 사용자가 검색 키워드를 언급한 경우 — user_search_summaries 를 사용.
        사용자가 "중요한" / "top" 아이템을 원할 때 — user_list_top_summaries 를 사용.
        사용자가 "내 구독 카테고리만" 이라고 명시한 경우 — user_get_my_briefing 을 사용.
        **결정 규칙:** 키워드도 중요도 필터도 없으면 이 도구. 카테고리는 선택이고,
        "오늘 뉴스" / "최근에 뭐 있어" 처럼 카테고리가 없으면 전체 카테고리에서 최신순으로 끌어온다.
        **파라미터:** category (선택, 카테고리 ID 또는 이름 — 생략 시 전체), limit (1~30, 기본 10), sinceDays (1~30, 기본 7).
        **반환:** 생성 시각 내림차순으로 정렬된 SummaryView 리스트.

        <examples>
        <example>
        user: "AI 카테고리 최근 뉴스"
        call: user_list_recent_summaries(category="AI")
        </example>
        <example>
        user: "경제 카테고리 최근 3일 top 5"
        call: user_list_recent_summaries(category="경제", limit=5, sinceDays=3)
        </example>
        <example>
        user: "오늘 뉴스 다 보여줘"
        call: user_list_recent_summaries(sinceDays=1)
        </example>
        <example>
        user: "요즘 올라온 거 모아봐"
        call: user_list_recent_summaries()
        </example>
        </examples>

        [category: list]
        """,
    )
    fun user_list_recent_summaries(
        @ToolParam(
            description = "카테고리 ID 또는 이름 (선택, 생략 시 전체 카테고리)",
            required = false,
        ) category: String? = null,
        @ToolParam(description = "최대 결과 수 (1~30, 기본 10)", required = false) limit: Int = 10,
        @ToolParam(description = "최근 N일 이내 아이템만 포함 (1~30, 기본 7)", required = false) sinceDays: Int = 7,
    ): String = mcpToolCall {
        validateLimit(limit)
        validateSinceDays(sinceDays)

        val resolvedCategory = category?.takeIf { it.isNotBlank() }
        if (resolvedCategory == null) {
            // 전체 카테고리 최근순 조회 — store 의 cross-category 경로에 위임한다.
            val result = clippingQueryPort.listRecentAcrossCategories(sinceDays, limit)
            val categoryNameById = buildCategoryNameMap()
            result.summaries.map { toSanitizedView(it, categoryNameById[it.categoryId] ?: "") }
        } else {
            val cat = categoryService.resolveCategory(resolvedCategory)
            // 카테고리 지정 조회도 중요도순 getSummaries가 아니라 최신순 전용 경로로 위임한다.
            val result = clippingQueryPort.listRecentForCategory(cat.id, sinceDays, limit)
            result.summaries.map { toSanitizedView(it, cat.name) }
        }
    }

    @Tool(
        description = """
        클리핑 요약에 대해 키워드 검색을 수행한다. 카테고리와 날짜 범위는 선택적이다.
        **언제 쓰나:** 사용자가 "AI 규제", "애플 실적", "MegaCorp 반도체" 같은 주제/키워드를 언급할 때.
        **쓰지 말 것:** 사용자가 키워드를 제시하지 않았을 때 — user_list_recent_summaries 를 사용.
        **결정 규칙:** 사용자가 주제/키워드를 명시적으로 언급하면 이 도구.
        키워드 없이 "최근 뉴스/새로 나온 것" 이면 user_list_recent_summaries,
        "중요한/핵심/top" 표현이면 user_list_top_summaries.
        **파라미터:** query 필수, category 선택 (생략 시 전체 카테고리 검색),
        fromDate / toDate 선택 (ISO yyyy-MM-dd), limit (1~30, 기본 10).
        **반환:** 키워드에 매치되는 SummaryView 리스트.

        <examples>
        <example>
        user: "AI 규제 관련 뉴스 찾아줘"
        call: user_search_summaries(query="AI 규제")
        </example>
        <example>
        user: "경제 카테고리에서 애플 실적 2026-04 이후"
        call: user_search_summaries(query="애플 실적", category="경제", fromDate="2026-04-01")
        </example>
        </examples>

        [category: search]
        """,
    )
    fun user_search_summaries(
        @ToolParam(description = "검색 키워드") query: String,
        @ToolParam(description = "카테고리 ID 또는 이름 (선택)", required = false) category: String? = null,
        @ToolParam(description = "시작일 yyyy-MM-dd (선택)", required = false) fromDate: String? = null,
        @ToolParam(description = "종료일 yyyy-MM-dd (선택)", required = false) toDate: String? = null,
        @ToolParam(description = "최대 결과 수 (1~30, 기본 10)", required = false) limit: Int = 10,
    ): String = mcpToolCall {
        validateLimit(limit)
        // 카테고리 지정이 있으면 해석하고, 없으면 전체 카테고리 검색으로 위임한다.
        val resolvedCategoryId = category
            ?.takeIf { it.isNotBlank() }
            ?.let { categoryService.resolveCategory(it).id }
        val from = parseLocalDate(fromDate)
        val to = parseLocalDate(toDate)
        val result = clippingQueryPort.searchSummaries(
            categoryId = resolvedCategoryId,
            query = query,
            fromDate = from,
            toDate = to,
            limit = limit,
        )
        val categoryNameById = buildCategoryNameMap()
        result.summaries.map { info ->
            toSanitizedView(info, categoryNameById[info.categoryId] ?: "")
        }
    }

    @Tool(
        description = """
        특정 카테고리에서 최근 N일 동안 중요도가 높은 상위 요약을 반환한다.
        **언제 쓰나:** 사용자가 "중요한 뉴스", "highlights", "top stories", "핵심 이슈" 를 요청할 때.
        **쓰지 말 것:** 사용자가 키워드 검색을 원할 때 — user_search_summaries 를 사용.
        사용자가 중요도 필터 없는 단순 목록을 원할 때 — user_list_recent_summaries 를 사용.
        **결정 규칙:** 사용자가 "중요한/핵심/top/highlights" 같은 중요도 표현을 쓰면 이 도구.
        키워드가 있으면 user_search_summaries, 단순 최근 목록이면 user_list_recent_summaries.
        **파라미터:** category (ID 또는 이름), days (1~30, 기본 7),
        minScore (0.0~1.0, 기본 0.7), limit (1~30, 기본 10).
        **반환:** 중요도 내림차순으로 정렬된 SummaryView 리스트.

        <examples>
        <example>
        user: "경제 카테고리 이번 주 핵심 이슈 top"
        call: user_list_top_summaries(category="경제")
        </example>
        <example>
        user: "AI 분야 최근 30일 highlights minScore 0.8"
        call: user_list_top_summaries(category="AI", days=30, minScore=0.8)
        </example>
        </examples>

        [category: analyze]
        """,
    )
    fun user_list_top_summaries(
        @ToolParam(description = "카테고리 ID 또는 이름") category: String,
        @ToolParam(description = "조회 기간 일수 (1~30, 기본 7)", required = false) days: Int = 7,
        @ToolParam(description = "최소 중요도 점수 (0.0~1.0, 기본 0.7)", required = false) minScore: Double = 0.7,
        @ToolParam(description = "최대 결과 수 (1~30, 기본 10)", required = false) limit: Int = 10,
    ): String = mcpToolCall {
        validateLimit(limit)
        validateSinceDays(days)
        validateScore(minScore)
        val cat = categoryService.resolveCategory(category)
        val result = clippingQueryPort.listTopSummaries(
            categoryId = cat.id,
            days = days,
            minScore = minScore,
            limit = limit,
        )
        result.summaries.map { toSanitizedView(it, cat.name) }
    }

    // -- private helpers --

    private fun toSanitizedView(info: SummaryInfo, categoryName: String): SummaryView {
        val base = SummaryView.from(
            info = info,
            categoryName = categoryName,
            sourceName = "",
            publishedAt = null,
        )
        // 프롬프트 인젝션 방어: 제목과 요약을 한 번 더 산타이즈한다.
        return base.copy(
            title = sanitizer.sanitize(base.title) ?: base.title,
            summary = sanitizer.sanitize(base.summary) ?: base.summary,
        )
    }

    private fun buildCategoryNameMap(): Map<String, String> =
        categoryService.listCategories().associate { it.id to it.name }

    private fun validateLimit(limit: Int) {
        if (limit < 1 || limit > 30) {
            throw InvalidInputException("limit must be between 1 and 30")
        }
    }

    private fun validateSinceDays(days: Int) {
        if (days < 1 || days > 30) {
            throw InvalidInputException("days must be between 1 and 30")
        }
    }

    private fun validateScore(score: Double) {
        if (score < 0.0 || score > 1.0) {
            throw InvalidInputException("minScore must be between 0.0 and 1.0")
        }
    }

    private fun parseInstant(text: String?): Instant? = try {
        text?.let { Instant.parse(it) }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseLocalDate(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return try {
            LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            throw InvalidInputException("Invalid date format (expected yyyy-MM-dd): $text")
        }
    }

}
