package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.model.OrganizationType
import com.ohmyclipping.service.OrganizationService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Admin 도구 — Phase 3 Organization 엔티티 조회.
 *
 * Organization 은 Category 와 many-to-many 로 연결되어
 * "경쟁사 A 관련 카테고리 트래픽" 같은 분석 질의를 받는다.
 */
@Component
class AdminOrganizationTools(
    private val organizationService: OrganizationService,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            조직(Organization) 목록을 반환한다. 타입 필터링 가능.
            **언제 쓰나:** 운영자가 "등록된 경쟁사 리스트", "고객사 목록", "조직 조회" 를 물어볼 때.
            **쓰지 말 것:** 특정 카테고리에 연결된 조직만 필요한 경우 — admin_category_organizations 를 사용.
            **파라미터:** type 선택 — "COMPETITOR"|"CUSTOMER"|"PARTNER"|"OTHER". 생략 시 전체.
            **반환:** Organization 리스트 (id, name, type, domain, description).
        """,
    )
    fun admin_list_organizations(
        @ToolParam(
            description = "타입 필터 (COMPETITOR|CUSTOMER|PARTNER|OTHER, 선택)",
            required = false,
        ) type: String?,
    ): String = mcpToolCall {
        val typeFilter = type?.takeIf { it.isNotBlank() }?.let(::parseType)
        rateLimiter.checkOrThrow("admin_list_organizations", maxRequests = 60, windowSeconds = 3600)
        organizationService.findAll(typeFilter)
    }

    @Tool(
        description = """
            특정 카테고리에 연결된 조직 목록을 반환한다.
            **언제 쓰나:** 운영자가 "이 카테고리의 경쟁사/고객사", "카테고리 매핑된 조직" 을 물어볼 때.
            **쓰지 말 것:** 전체 조직 목록이 필요한 경우 — admin_list_organizations 를 사용.
            **파라미터:** categoryId 필수.
            **반환:** 해당 카테고리에 매핑된 Organization 리스트. 매핑이 없으면 빈 배열.
        """,
    )
    fun admin_category_organizations(
        @ToolParam(description = "카테고리 ID") categoryId: String,
    ): String = mcpToolCall {
        if (categoryId.isBlank()) {
            throw InvalidInputException("categoryId is required")
        }
        rateLimiter.checkOrThrow("admin_category_organizations", maxRequests = 60, windowSeconds = 3600)
        organizationService.findByCategoryId(categoryId)
    }

    /** type 문자열을 OrganizationType enum 으로 변환하며, 허용 밖이면 InvalidInputException. */
    private fun parseType(raw: String): OrganizationType =
        runCatching { OrganizationType.valueOf(raw.trim().uppercase()) }.getOrElse {
            throw InvalidInputException(
                "Invalid organization type: '$raw'. Allowed: COMPETITOR, CUSTOMER, PARTNER, OTHER"
            )
        }
}
