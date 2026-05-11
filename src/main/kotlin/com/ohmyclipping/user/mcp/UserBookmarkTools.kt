package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.dto.BookmarkView
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.UserArticleHistoryService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 사용자 대상 MCP 도구 — 북마크 토글 및 조회.
 *
 * 호출자(orchestrator) 는 `_onBehalfOfUserId` 로 최종 사용자의 내부 ID 를 주입한다.
 * 북마크 테이블은 스냅샷 기반이라 원본 요약이 retention 으로 사라져도 유지된다.
 */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class UserBookmarkTools(
    private val userArticleHistoryService: UserArticleHistoryService,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
        현재 사용자의 기사 북마크를 토글한다 — 이미 북마크돼 있으면 제거, 없으면 추가.
        북마크 시점의 요약/제목/링크를 별도 스냅샷으로 복사해 원본 삭제 이후에도 유지된다.
        **언제 쓰나:** 사용자가 "이 기사 저장해줘", "나중에 보게 북마크", "북마크 취소" 같은 저장 의도를 표현할 때.
        **쓰지 말 것:** 사용자가 좋아요/별로 같은 평가를 원할 때 — user_toggle_feedback 사용.
        **파라미터:** summaryId 필수.
        **반환:** `{ summaryId, bookmarked: true|false }` — 토글 후 상태.

        <examples>
        <example>
        user: "이 기사 저장해줘"
        call: user_toggle_bookmark(summaryId="<현재 대화 context 의 summaryId>")
        </example>
        </examples>

        ⚠️ 쓰기 작업 — 토글이므로 두 번 호출하면 원래대로. summaryId 확인 후 신중히. [category: update]
        """,
    )
    @Suppress("FunctionParameterNaming")
    fun user_toggle_bookmark(
        @ToolParam(description = "북마크 토글 대상 요약 ID") summaryId: String,
        @ToolParam(
            description = "내부 주입: 북마크 주체 사용자 ID (LLM 비노출)",
            required = false,
        ) _onBehalfOfUserId: String?,
    ): String = mcpToolCall {
        // 호출 빈도 제한: 120회/시간 — 개인 클릭성 행동.
        rateLimiter.checkOrThrow("user_toggle_bookmark", maxRequests = 120, windowSeconds = 3600)
        val userId = _onBehalfOfUserId?.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException("Caller user id is not bound; orchestrator must inject _onBehalfOfUserId")
        val bookmarked = userArticleHistoryService.toggleBookmarkByUserId(userId, summaryId)
        mapOf(
            "summaryId" to summaryId,
            "bookmarked" to bookmarked,
        )
    }

    @Tool(
        description = """
        현재 사용자의 북마크 기사 목록을 최신 북마크 순으로 반환한다.
        **언제 쓰나:** 사용자가 "내가 저장한 기사", "북마크 보여줘", "내 보관함", "clipping 저장한 글" 을 요청할 때.
        **쓰지 말 것:** 사용자가 일반 기사 목록을 원하는 경우 — user_list_recent_summaries 사용.
        **파라미터:** limit (1~50, 기본 20), offset (0 이상, 기본 0).
        **반환:** BookmarkView 리스트 (summaryId, title, summary, sourceLink, articleCreatedAt, bookmarkedAt).

        <examples>
        <example>
        user: "내가 북마크한 기사 보여줘"
        call: user_list_bookmarks()
        </example>
        <example>
        user: "저장한 글 최근 5건"
        call: user_list_bookmarks(limit=5)
        </example>
        </examples>

        [category: list]
        """,
    )
    @Suppress("FunctionParameterNaming")
    fun user_list_bookmarks(
        @ToolParam(description = "최대 결과 수 (1~50, 기본 20)", required = false) limit: Int = 20,
        @ToolParam(description = "오프셋 (0 이상, 기본 0)", required = false) offset: Int = 0,
        @ToolParam(
            description = "내부 주입: 조회 주체 사용자 ID (LLM 비노출)",
            required = false,
        ) _onBehalfOfUserId: String?,
    ): String = mcpToolCall {
        // 읽기 전용이라 rate limit 은 여유있게 60/시간.
        rateLimiter.checkOrThrow("user_list_bookmarks", maxRequests = 60, windowSeconds = 3600)
        validateLimit(limit)
        validateOffset(offset)
        val userId = _onBehalfOfUserId?.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException("Caller user id is not bound; orchestrator must inject _onBehalfOfUserId")
        userArticleHistoryService.listBookmarksByUserId(userId, offset, limit)
            .map { BookmarkView.from(it) }
    }

    private fun validateLimit(limit: Int) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw InvalidInputException("limit must be between 1 and $MAX_LIMIT")
        }
    }

    private fun validateOffset(offset: Int) {
        if (offset < 0) {
            throw InvalidInputException("offset must be >= 0")
        }
    }

    private companion object {
        const val MAX_LIMIT = 50
    }
}
