package com.ohmyclipping.user.mcp

import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.dto.DtoSanitizer
import com.ohmyclipping.mcp.dto.OriginalContentView
import com.ohmyclipping.mcp.dto.SummaryDetailView
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.source.DomainExtractor
import com.ohmyclipping.service.port.ClippingQueryPort
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 사용자 대상 MCP 도구 — 요약 단건 상세 및 원문 프리뷰 조회.
 *
 * 저작권 보호를 위해 원문 전체 대신 [PREVIEW_LENGTH]자 프리뷰만 제공하며,
 * [DtoSanitizer]로 프롬프트 인젝션 패턴을 방어한다.
 */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class UserSummaryDetailTools(
    private val clippingQueryPort: ClippingQueryPort,
    private val sanitizer: DtoSanitizer,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            ID 로 단일 클리핑 요약의 상세 정보를 조회한다.
            **언제 쓰나:** 사용자가 특정 기사를 선택했거나 "자세히", "상세 보기", "이 기사 더 알려줘" 를 요청했을 때.
            **쓰지 말 것:** 사용자가 목록을 원할 때 — user_list_recent_summaries 또는 user_search_summaries 를 사용.
                             사용자가 원문 본문을 원할 때 — user_get_original_preview 를 사용.
            **파라미터:** summaryId = BatchSummary ID (이전 목록 도구가 반환한 값).
            **반환:** title, summary, keywords, preview, 저작권 안내가 담긴 SummaryDetailView.
        """,
    )
    fun user_get_summary_detail(
        @ToolParam(description = "BatchSummary ID") summaryId: String,
    ): String = mcpToolCall {
        // 서비스에서 상세 결과를 가져와 DTO로 변환한다.
        val detail = clippingQueryPort.getSummaryDetail(summaryId)
        val sourceName = DomainExtractor.extract(detail.sourceLink) ?: ""
        val title = detail.translatedTitle ?: detail.originalTitle
        val preview = detail.contentPreview.orEmpty()

        SummaryDetailView(
            id = detail.id,
            categoryId = detail.categoryId,
            categoryName = detail.categoryName,
            title = sanitizer.sanitize(title) ?: title,
            summary = sanitizer.sanitize(detail.summary) ?: detail.summary,
            sourceLink = detail.sourceLink,
            sourceName = sourceName,
            publishedAt = null,
            importanceScore = detail.importanceScore.toDouble(),
            keywords = detail.keywords,
            createdAt = detail.createdAt,
            previewMarkdown = sanitizer.sanitize(preview) ?: preview,
            copyrightNotice = buildCopyrightNotice(sourceName),
        )
    }

    @Tool(
        description = """
            summary ID 로 원문 기사 본문의 짧은 프리뷰를 반환한다 (최대 300자).
            **언제 쓰나:** 사용자가 "원문 일부 보여줘", "기사 일부 인용해줘", "도입부 보여줘" 등을 요청할 때.
            **쓰지 말 것:** 사용자가 AI 요약을 원할 때 — user_get_summary_detail 을 사용.
                             사용자가 전체 기사를 원할 때 — sourceLink 를 그대로 안내.
            **파라미터:** summaryId = BatchSummary ID.
            **반환:** 잘려진 마크다운 프리뷰와 저작권 안내가 담긴 OriginalContentView.
        """,
    )
    fun user_get_original_preview(
        @ToolParam(description = "BatchSummary ID") summaryId: String,
    ): String = mcpToolCall {
        // 호출 빈도 제한: 최대 60회/시간. 원문 프리뷰는 저작권 보호 대상이라 보수적으로 제한한다.
        rateLimiter.checkOrThrow("user_get_original_preview", maxRequests = 60, windowSeconds = 3600)
        // 요약 상세에서 sourceLink를 얻고 원문 본문을 로드한다.
        val detail = clippingQueryPort.getSummaryDetail(summaryId)
        val sourceLink = detail.sourceLink
        val sourceName = DomainExtractor.extract(sourceLink) ?: ""

        // 원문 마크다운을 조회한 뒤 경계 기준으로 PREVIEW_LENGTH자까지 잘라낸다.
        val original = clippingQueryPort.getOriginalContent(sourceLink)
        val truncated = truncateAtWordBoundary(original.markdown, PREVIEW_LENGTH)
        val preview = if (original.markdown.length > truncated.length) "$truncated..." else truncated

        OriginalContentView(
            summaryId = detail.id,
            title = sanitizer.sanitize(original.title) ?: original.title,
            sourceName = sourceName,
            author = null,
            publishedAt = null,
            canonicalUrl = sourceLink,
            previewMarkdown = sanitizer.sanitize(preview) ?: preview,
            copyrightNotice = buildCopyrightNotice(sourceName),
        )
    }

    // -- private helpers --

    private fun buildCopyrightNotice(sourceName: String): String {
        val displayName = sourceName.ifBlank { "원본 매체" }
        return "\u00A9 $displayName. 전체 내용은 원본 링크를 참고하세요."
    }

    /** 지정 길이까지 자르되, 가능한 경우 직전 공백 경계에서 종료한다. */
    private fun truncateAtWordBoundary(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        val hardCut = text.substring(0, maxLength)
        val lastBoundary = hardCut.lastIndexOfAny(charArrayOf(' ', '\n', '.', '。'))
        return if (lastBoundary >= maxLength / 2) hardCut.substring(0, lastBoundary) else hardCut
    }

    companion object {
        private const val PREVIEW_LENGTH = 300
    }
}
