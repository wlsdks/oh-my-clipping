package com.ohmyclipping.user.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmyclipping.mcp.FilteredToolCallback
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.dto.DtoSanitizer
import com.ohmyclipping.service.SummaryFeedbackService
import com.ohmyclipping.service.UserArticleHistoryService
import com.ohmyclipping.service.UserSubscriptionQueryService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.method.MethodToolCallbackProvider

/**
 * 실제 user MCP 도구의 tools/list 스키마에서 시스템 주입 파라미터가 숨겨지는지 검증한다.
 *
 * `_onBehalfOfUserId` 는 orchestrator 가 인증 컨텍스트로 주입해야 하며, LLM 이 임의로
 * 채우면 사용자 경계가 깨질 수 있다. 단순 래퍼 단위 테스트뿐 아니라 실제 도구 정의로
 * 스키마를 만들어 회귀를 잡는다.
 */
class UserToolSchemaHiddenParamsTest {

    private val objectMapper = ObjectMapper()
    private val rateLimiter = mockk<McpRateLimiter>(relaxed = true)

    @Test
    fun `user feedback bookmark subscription tools hide on-behalf-of user parameter`() {
        val provider = MethodToolCallbackProvider.builder()
            .toolObjects(
                UserFeedbackTools(
                    summaryFeedbackService = mockk<SummaryFeedbackService>(relaxed = true),
                    rateLimiter = rateLimiter,
                ),
                UserBookmarkTools(
                    userArticleHistoryService = mockk<UserArticleHistoryService>(relaxed = true),
                    rateLimiter = rateLimiter,
                ),
                UserSubscriptionTools(
                    subscriptionQueryService = mockk<UserSubscriptionQueryService>(relaxed = true),
                    sanitizer = DtoSanitizer(),
                    rateLimiter = rateLimiter,
                ),
            )
            .build()

        val hiddenParamTools = setOf(
            "user_toggle_feedback",
            "user_toggle_bookmark",
            "user_list_bookmarks",
            "user_list_my_subscriptions",
            "user_get_my_briefing",
        )

        val callbacksByName = provider.toolCallbacks.associateBy { it.toolDefinition.name() }
        callbacksByName.keys.containsAll(hiddenParamTools) shouldBe true

        hiddenParamTools.forEach { toolName ->
            val originalSchema = callbacksByName.getValue(toolName).toolDefinition.inputSchema()
            originalSchema shouldContain "_onBehalfOfUserId"

            val filteredSchema = FilteredToolCallback(callbacksByName.getValue(toolName), objectMapper)
                .toolDefinition
                .inputSchema()

            filteredSchema shouldNotContain "_onBehalfOfUserId"
            val required = objectMapper.readTree(filteredSchema).get("required")
            required?.map { it.asText() }?.none { it.startsWith("_") } shouldBe true
        }
    }
}
