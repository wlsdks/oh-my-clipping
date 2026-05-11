package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.dto.BriefingSection
import com.ohmyclipping.mcp.dto.BriefingView
import com.ohmyclipping.mcp.dto.DtoSanitizer
import com.ohmyclipping.mcp.dto.SubscriptionView
import com.ohmyclipping.mcp.dto.SummaryView
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.UserSubscriptionQueryService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 사용자 대상 MCP 도구 — 내 구독 카테고리 목록 + 발송 스케줄 메타 조회.
 *
 * `_onBehalfOfUserId` 는 orchestrator 가 인증 컨텍스트에서 주입하는 내부 사용자 ID.
 * 폴백 규칙: 구독별 규칙(CategoryRule)이 NULL 이면 글로벌 UserDeliverySchedule 사용,
 * 글로벌도 없으면 기본값(평일 08시) 을 적용한다.
 */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class UserSubscriptionTools(
    private val subscriptionQueryService: UserSubscriptionQueryService,
    private val sanitizer: DtoSanitizer,
    private val rateLimiter: McpRateLimiter,
) {

    @Tool(
        description = """
            현재 사용자의 구독 카테고리 + 발송 일정을 반환한다 (메타만, 기사 내용은 포함하지 않음).
            **언제 쓰나:** "내 구독", "뭐 받고 있어?", "발송 일정 보여줘" 처럼 본인 구독 메타/스케줄 질문.
            **쓰지 말 것:**
              - 전체 공개 카테고리 목록 → user_list_categories
              - 구독 카테고리의 최근 기사를 같이 보고 싶다 → user_get_my_briefing
            **파라미터:** 없음 (orchestrator 가 userId 자동 주입).
            **반환:** SubscriptionView[] — 각 항목 필드:
              - categoryId, categoryName, categoryDescription
              - deliveryDays: ["MON","TUE",...] 발송 요일 배열
              - deliveryHour: 0~23 발송 시각 (KST 기준 시)
              - deliveryDaysSource: "category" | "global" | "default" — 이 일정이 어디서 나왔는지
            **폴백:** 카테고리별 규칙(CategoryRule) → 글로벌 일정 → 기본값(평일 08시) 순서.
            **빈 구독:** 빈 배열 반환 (에러 아님).
        """,
    )
    @Suppress("FunctionParameterNaming")
    fun user_list_my_subscriptions(
        @ToolParam(
            description = "내부 주입: 조회 주체 사용자 ID (LLM 비노출)",
            required = false,
        ) _onBehalfOfUserId: String?,
    ): String = mcpToolCall {
        // 읽기 전용 — 60회/시간.
        rateLimiter.checkOrThrow("user_list_my_subscriptions", maxRequests = 60, windowSeconds = 3600)
        val userId = _onBehalfOfUserId?.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException("Caller user id is not bound; orchestrator must inject _onBehalfOfUserId")

        // 구독/스케줄 폴백 정책은 service에 위임하고 도구는 MCP 응답 형식만 만든다.
        subscriptionQueryService.listMySubscriptions(userId)
            .map { item ->
                SubscriptionView(
                    categoryId = item.categoryId,
                    categoryName = item.categoryName,
                    categoryDescription = item.categoryDescription,
                    deliveryDays = item.deliveryDays,
                    deliveryHour = item.deliveryHour,
                    deliveryDaysSource = item.deliveryDaysSource,
                )
            }
    }

    @Tool(
        description = """
            현재 사용자의 구독 카테고리의 최근 요약을 카테고리별로 한 번에 묶어 반환한다 (단일 쿼리).
            "내 구독 / 내 브리핑 / 아침 체크" 류 요청에 대해 arc-reactor 가 여러 도구를 체인하지 않고
            단일 호출로 답할 수 있게 한다.
            **언제 쓰나:** 사용자가 "내 구독 카테고리 오늘 뭐 있어?", "내 브리핑 보여줘", "아침 체크" 처럼
            본인이 구독한 카테고리만 모아 보고 싶을 때.
            **쓰지 말 것:** 전체 공개 카테고리 브라우징은 user_list_recent_summaries 사용.
            특정 카테고리 하나만 보려면 user_list_recent_summaries(category=...) 사용.
            구독 메타(발송 일정)만 보려면 user_list_my_subscriptions 사용.
            **파라미터:** sinceDays (1~30, 기본 1 = 어제~오늘), perCategoryLimit (1~10, 기본 5).
            **반환:** BriefingView — sections[{categoryId, categoryName, summaries: [...]}] + sinceDays/perCategoryLimit.
            **빈 구독:** sections=[] + emptyNote="구독 중인 카테고리가 없습니다" 반환 (에러 아님).

            <examples>
            <example>
            user: "내 구독 오늘 것만 보여줘"
            call: user_get_my_briefing(sinceDays=1)
            </example>
            <example>
            user: "내 브리핑 카테고리마다 3개씩 최근 이틀"
            call: user_get_my_briefing(sinceDays=2, perCategoryLimit=3)
            </example>
            </examples>
        """,
    )
    @Suppress("FunctionParameterNaming")
    fun user_get_my_briefing(
        @ToolParam(description = "최근 N일 이내 아이템만 포함 (1~30, 기본 1)", required = false)
        sinceDays: Int = 1,
        @ToolParam(description = "카테고리당 최대 요약 수 (1~10, 기본 5)", required = false)
        perCategoryLimit: Int = 5,
        @ToolParam(
            description = "내부 주입: 조회 주체 사용자 ID (LLM 비노출)",
            required = false,
        ) _onBehalfOfUserId: String?,
    ): String = mcpToolCall {
        // 읽기 전용 — 60회/시간. user_list_my_subscriptions 와 동급.
        rateLimiter.checkOrThrow("user_get_my_briefing", maxRequests = 60, windowSeconds = 3600)
        if (sinceDays !in 1..30) throw InvalidInputException("sinceDays must be between 1 and 30")
        if (perCategoryLimit !in 1..10) throw InvalidInputException("perCategoryLimit must be between 1 and 10")

        val userId = _onBehalfOfUserId?.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException("Caller user id is not bound; orchestrator must inject _onBehalfOfUserId")

        // 카테고리별 조회 정책은 service가 수행하고 도구는 sanitizing과 MCP DTO 변환만 담당한다.
        val briefing = subscriptionQueryService.getMyBriefing(userId, sinceDays, perCategoryLimit)
        val sections = briefing.sections.map { section ->
            val recent = section.summaries.map { info ->
                val base = SummaryView.from(
                    info = info,
                    categoryName = section.categoryName,
                    sourceName = "",
                    publishedAt = null,
                )
                base.copy(
                    title = sanitizer.sanitize(base.title) ?: base.title,
                    summary = sanitizer.sanitize(base.summary) ?: base.summary,
                )
            }
            BriefingSection(
                categoryId = section.categoryId,
                categoryName = section.categoryName,
                summaries = recent,
            )
        }

        BriefingView(
            sinceDays = briefing.sinceDays,
            perCategoryLimit = briefing.perCategoryLimit,
            sections = sections,
            emptyNote = briefing.emptyNote,
        )
    }
}
