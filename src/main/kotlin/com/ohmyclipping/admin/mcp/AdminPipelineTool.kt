package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.AdminClippingService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — collect → summarize → digest 조립 파이프라인.
 *
 * Slack 게시 없이 미리보기 용도로만 실행한다 (sendToSlack=false 고정).
 * 실제 발송은 [AdminSendDigestTool]로 수행한다.
 */
@Component
class AdminPipelineTool(
    private val adminClippingService: AdminClippingService,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            하나의 카테고리에 대해 collect → summarize → digest 파이프라인을 전부 실행한다 (Slack 발송 없음, 읽기 전용 미리보기).
            **언제 쓰나:** 실제 발송 전에, 게시될 digest 를 그대로 미리 확인하고 싶을 때.
            **쓰지 말 것:** 사용자가 이미 발송을 확정했을 때 — admin_send_digest 를 사용.
            **파라미터:** categoryId 필수, hoursBack/maxItems/unsentOnly 선택.
            **반환:** digest 미리보기가 담긴 PipelineResult.
        """,
    )
    fun admin_pipeline(
        @ToolParam(description = "파이프라인을 실행할 카테고리 ID") categoryId: String,
        @ToolParam(description = "몇 시간 이전까지 조회할지 (기본값은 런타임 설정)", required = false) hoursBack: Int?,
        @ToolParam(description = "digest 최대 아이템 수 (1~5)", required = false) maxItems: Int?,
        @ToolParam(description = "미발송 요약만 포함할지 여부 (기본 true)", required = false) unsentOnly: Boolean?,
        // Ralph 루프 override 들은 LLM 에게 노출하지 않는다. FilteredToolCallback 이 `_` prefix
        // 파라미터를 tools/list 스키마에서 제거하므로 LLM 이 이 값을 임의로 채울 수 없다.
        // 내부 스크립트/오케스트레이터가 필요한 경우에만 수동으로 전달하도록 남겨둔다.
        @ToolParam(
            description = "이번 실행에서 Ralph 루프 모드를 사용할지 여부 (내부 override, LLM 비노출)",
            required = false,
        ) _ralphLoopEnabled: Boolean?,
        @ToolParam(
            description = "이번 실행의 Ralph 루프 최대 반복 횟수 (내부 override, LLM 비노출)",
            required = false,
        ) _ralphLoopMaxIterations: Int?,
        @ToolParam(
            description = "Ralph 루프 종료 조건 문구 (내부 override, LLM 비노출)",
            required = false,
        ) _ralphLoopStopPhrase: String?,
    ): String = mcpToolCall {
        // 호출 빈도 제한: 최대 5회/시간. 파이프라인은 비용이 크다.
        rateLimiter.checkOrThrow("admin_pipeline", maxRequests = 5, windowSeconds = 3600)
        adminClippingService.runPipeline(
            categoryId = categoryId,
            hoursBack = hoursBack,
            maxItems = maxItems,
            unsentOnly = unsentOnly ?: true,
            sendToSlack = false,
            slackChannelId = null,
            ralphLoopEnabledOverride = _ralphLoopEnabled,
            ralphLoopMaxIterationsOverride = _ralphLoopMaxIterations,
            ralphLoopStopPhraseOverride = _ralphLoopStopPhrase,
        )
    }
}
