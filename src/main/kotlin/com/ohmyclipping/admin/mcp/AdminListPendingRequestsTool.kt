package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
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

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 50
    }

    @Tool(
        description = """
            운영자 검토를 기다리는 사용자 구독 신청 목록을 반환한다.
            **언제 쓰나:** 운영자가 "pending requests", "뭐 승인 대기 중이야", "대기 중인 신청" 을 물어볼 때.
            **쓰지 말 것:** 승인/반려 액션이 필요할 때 — 해당 액션은 안전을 위해 웹 어드민에서만 가능.
            **파라미터:** limit 선택 (1~50, 기본 20).
            **반환:** PENDING 상태인 UserClippingRequest 리스트.
        """,
    )
    fun admin_list_pending_requests(
        @ToolParam(description = "최대 결과 수 (1~50, 기본 20)", required = false) limit: Int?,
    ): String = mcpToolCall {
        val effectiveLimit = validateLimit(limit)
        userClippingRequestService.listRecentRequests(UserClippingRequestStatus.PENDING, effectiveLimit)
    }

    private fun validateLimit(limit: Int?): Int {
        val effective = limit ?: DEFAULT_LIMIT
        if (effective !in MIN_LIMIT..MAX_LIMIT) {
            throw InvalidInputException("limit must be between $MIN_LIMIT and $MAX_LIMIT")
        }
        return effective
    }
}
