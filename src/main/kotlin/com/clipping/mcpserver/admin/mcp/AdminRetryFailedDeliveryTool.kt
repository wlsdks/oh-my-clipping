package com.clipping.mcpserver.admin.mcp

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.mcp.McpRateLimiter
import com.clipping.mcpserver.mcp.mcpToolCall
import com.clipping.mcpserver.service.DeliveryAdminService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 실패 상태의 Slack 다이제스트 발송을 재시도 대기열에 되돌린다.
 *
 * ⚠️ 외부 액션: 재시도 워커가 픽업하면 Slack 채널에 다시 메시지가 게시된다.
 * 운영자가 명시적으로 확인한 뒤 호출해야 한다.
 */
@Component
class AdminRetryFailedDeliveryTool(
    private val deliveryAdminService: DeliveryAdminService,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            FAILED / FINALIZATION_FAILED 상태의 발송 건을 재시도 큐에 되돌린다.
            **⚠️ 외부 액션:** 재시도 워커가 다시 Slack 채널에 게시한다. 멱등하지 않으며 취소 불가.
            **언제 쓰나:** 운영자가 특정 실패 건을 명시적으로 재발송하기로 승인한 경우에만.
            **쓰지 말 것:** 자동으로 실패 큐를 돌리고 싶을 때 — 스케줄러가 처리하므로 불필요.
            **파라미터:** deliveryLogId 필수 — 기존 `GET /api/admin/delivery` 로 얻은 발송 로그 id.
            **반환:** `{ success, deliveryLogId }`.
        """,
    )
    fun admin_retry_failed_delivery(
        @ToolParam(description = "재시도 대상 발송 로그 ID") deliveryLogId: String,
    ): String = mcpToolCall {
        // 빈도 제한: 30회/시간 — 사람이 수동 재시도 보내는 빈도 상한.
        rateLimiter.checkOrThrow("admin_retry_failed_delivery", maxRequests = 30, windowSeconds = 3600)
        if (deliveryLogId.isBlank()) {
            throw InvalidInputException("deliveryLogId is required")
        }
        deliveryAdminService.retryDelivery(deliveryLogId)
        mapOf(
            "success" to true,
            "deliveryLogId" to deliveryLogId,
        )
    }
}
