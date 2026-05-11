package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.SummaryFeedbackService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 사용자 대상 MCP 도구 — 요약에 대한 개인 반응(LIKE/DISLIKE/NEUTRAL/NONE) 저장.
 *
 * 호출자(orchestrator) 는 최종 사용자의 내부 ID 를 `_onBehalfOfUserId` 로 주입해야 한다.
 * 이 파라미터는 [com.ohmyclipping.mcp.FilteredToolCallback] 규칙으로 LLM 에 비노출된다.
 * 호출 발화자는 자신의 ID 를 직접 채울 수 없으므로 시스템이 인증 세션에서 안전하게 주입한다.
 */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class UserFeedbackTools(
    private val summaryFeedbackService: SummaryFeedbackService,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            특정 요약(기사)에 대한 감정 반응을 저장한다 (좋아요/보통/별로/해제).
            ⚠️ **북마크와 다름**: 좋아요는 "저장" 이 아니다. 나중에 다시 보고 싶어서 저장하는 거라면 user_toggle_bookmark 를 쓴다.
            **특징:** 같은 (사용자, 기사) 쌍은 1개 반응만 유지 — 여러 번 호출하면 마지막 값만 남는다 (upsert).
            **언제 쓰나:** "이 기사 좋아", "이건 별로", "반응 취소" 같은 감정/평가 표현.
            **쓰지 말 것:** "나중에 보게 저장해줘" 같은 저장 의도 — user_toggle_bookmark.
            **파라미터:**
              - summaryId: 필수 — 반응 대상 요약 ID
              - reaction: 필수 — LIKE | DISLIKE | NEUTRAL | NONE. NONE 은 기존 반응 삭제.
            **반환:** {summaryId, reaction (저장된 현재 값), message (사람이 읽을 한국어 안내)}.
            **rate limit:** 120회/시간 (개인 클릭성 행동이라 여유 있음).
        """,
    )
    @Suppress("FunctionParameterNaming")
    fun user_toggle_feedback(
        @ToolParam(description = "반응을 남길 요약(BatchSummary) ID") summaryId: String,
        @ToolParam(
            description = "반응 종류 (LIKE|DISLIKE|NEUTRAL|NONE). NONE 은 기존 반응을 해제한다."
        ) reaction: String,
        // 내부 주입 — FilteredToolCallback 이 `_` prefix 파라미터를 tools/list 스키마에서 제거.
        @ToolParam(
            description = "내부 주입: 반응 주체 사용자 ID (LLM 비노출)",
            required = false,
        ) _onBehalfOfUserId: String?,
    ): String = mcpToolCall {
        // 호출 빈도 제한: 120회/시간 — 개인 클릭성 행동에 여유를 둠.
        rateLimiter.checkOrThrow("user_toggle_feedback", maxRequests = 120, windowSeconds = 3600)
        val userId = _onBehalfOfUserId?.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException("Caller user id is not bound; orchestrator must inject _onBehalfOfUserId")
        val (feedback, message) = summaryFeedbackService.upsertFromMcp(userId, summaryId, reaction)
        mapOf(
            "summaryId" to summaryId,
            "reaction" to (feedback?.feedbackType ?: "NONE"),
            "message" to message,
        )
    }
}
