package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.service.UserClippingRequestService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 승인 대기 중인 사용자 구독 요청 목록.
 *
 * 승인/반려 액션 자체는 안전을 위해 웹 어드민에서만 수행한다.
 */
@Component
class AdminListPendingRequestsTool(private val userClippingRequestService: UserClippingRequestService) {

    @Tool(
        description = """
            운영자 검토를 기다리는 사용자 구독 신청 목록을 반환한다.
            **언제 쓰나:** 운영자가 "pending requests", "뭐 승인 대기 중이야", "대기 중인 신청" 을 물어볼 때.
            **쓰지 말 것:** 승인/반려 액션이 필요할 때 — 해당 액션은 안전을 위해 웹 어드민에서만 가능.
            **파라미터:** limit 선택 (기본 20, 최대 50).
            **반환:** PENDING 상태인 UserClippingRequest 리스트.
        """,
    )
    fun admin_list_pending_requests(
        @ToolParam(description = "최대 결과 수 (기본 20, 최대 50)", required = false) limit: Int?,
    ): String = mcpToolCall {
        // 상한 50으로 제한해 과도한 응답을 막는다.
        val effectiveLimit = (limit ?: 20).coerceIn(1, 50)
        userClippingRequestService.listRecentRequests(UserClippingRequestStatus.PENDING, effectiveLimit)
    }
}
