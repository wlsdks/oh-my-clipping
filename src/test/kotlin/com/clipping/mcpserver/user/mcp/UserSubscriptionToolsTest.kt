package com.clipping.mcpserver.user.mcp

import com.clipping.mcpserver.error.RateLimitExceededException
import com.clipping.mcpserver.mcp.McpRateLimiter
import com.clipping.mcpserver.mcp.dto.DtoSanitizer
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.service.dto.clipping.SummaryInfo
import com.clipping.mcpserver.model.UserDeliverySchedule
import com.clipping.mcpserver.service.port.ClippingQueryPort
import com.clipping.mcpserver.service.UserSubscriptionQueryService
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.UserDeliveryScheduleStore
import com.clipping.mcpserver.store.UserOwnedCategoryStore
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * user_list_my_subscriptions 단위 테스트.
 *
 * 검증 포인트:
 *  - 구독이 있을 때: 카테고리 메타 + 발송 스케줄(폴백 포함) JSON 반환.
 *  - 구독이 없을 때: 빈 배열 반환.
 *  - rate limit 초과 시 JSON-RPC 에러.
 *  - `_onBehalfOfUserId` 누락 시 거부.
 */
class UserSubscriptionToolsTest {

    private val userOwnedCategoryStore = mockk<UserOwnedCategoryStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val userDeliveryScheduleStore = mockk<UserDeliveryScheduleStore>()
    private val clippingService = mockk<ClippingQueryPort>()
    private val subscriptionQueryService = UserSubscriptionQueryService(
        userOwnedCategoryStore = userOwnedCategoryStore,
        categoryStore = categoryStore,
        categoryRuleStore = categoryRuleStore,
        userDeliveryScheduleStore = userDeliveryScheduleStore,
        clippingQueryPort = clippingService,
    )
    private val sanitizer = DtoSanitizer()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = UserSubscriptionTools(
        subscriptionQueryService = subscriptionQueryService,
        sanitizer = sanitizer,
        rateLimiter = rateLimiter,
    )

    private fun summaryInfo(id: String, categoryId: String, createdAt: String) = SummaryInfo(
        id = id,
        originalTitle = "Title $id",
        translatedTitle = "번역 $id",
        summary = "요약 $id",
        keywords = listOf("news"),
        importanceScore = 0.8f,
        sourceLink = "https://example.com/$id",
        isSentToSlack = false,
        categoryId = categoryId,
        createdAt = createdAt,
    )

    @Nested
    inner class `user_list_my_subscriptions 호출 시` {

        @Test
        fun `소유 카테고리에 규칙이 있으면 category 스케줄을 사용한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userOwnedCategoryStore.listCategoryIds("u1") } returns listOf("c1")
            every { userDeliveryScheduleStore.findByUserId("u1") } returns
                UserDeliverySchedule(userId = "u1", deliveryDays = listOf("SAT"), deliveryHour = 18)
            every { categoryStore.listByIds(setOf("c1")) } returns listOf(
                Category(id = "c1", name = "AI", description = "AI 뉴스"),
            )
            every { categoryRuleStore.findByCategoryId("c1") } returns CategoryRule(
                categoryId = "c1",
                deliveryDays = listOf("MON", "WED"),
                deliveryHour = 12,
            )

            val json = tool.user_list_my_subscriptions(_onBehalfOfUserId = "u1")

            json shouldContain "\"categoryId\":\"c1\""
            json shouldContain "\"deliveryDaysSource\":\"category\""
            json shouldContain "\"deliveryHour\":12"
            json shouldNotContain "\"categoryId\":\"c2\""
        }

        @Test
        fun `카테고리 규칙이 없고 글로벌이 있으면 global 폴백한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userOwnedCategoryStore.listCategoryIds("u1") } returns listOf("c1")
            every { userDeliveryScheduleStore.findByUserId("u1") } returns
                UserDeliverySchedule(userId = "u1", deliveryDays = listOf("TUE", "THU"), deliveryHour = 18)
            every { categoryStore.listByIds(setOf("c1")) } returns listOf(
                Category(id = "c1", name = "AI"),
            )
            every { categoryRuleStore.findByCategoryId("c1") } returns null

            val json = tool.user_list_my_subscriptions(_onBehalfOfUserId = "u1")

            json shouldContain "\"deliveryDaysSource\":\"global\""
            json shouldContain "\"deliveryHour\":18"
        }

        @Test
        fun `소유 카테고리가 없으면 빈 배열을 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userOwnedCategoryStore.listCategoryIds("u1") } returns emptyList()

            val json = tool.user_list_my_subscriptions(_onBehalfOfUserId = "u1")

            assert(json == "[]") { "expected empty array but got: $json" }
            verify(exactly = 0) { categoryStore.listByIds(any()) }
        }

        @Test
        fun `rate limit 초과 시 서비스는 호출되지 않는다`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "user_list_my_subscriptions",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.user_list_my_subscriptions(_onBehalfOfUserId = "u1")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { userOwnedCategoryStore.listCategoryIds(any()) }
        }
    }

    @Nested
    inner class `user_get_my_briefing 호출 시` {

        @Test
        fun `구독 카테고리별로 최근 요약을 묶어 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userOwnedCategoryStore.listCategoryIds("u1") } returns listOf("c1", "c2")
            every { categoryStore.listByIds(setOf("c1", "c2")) } returns listOf(
                Category(id = "c1", name = "AI"),
                Category(id = "c2", name = "Economy"),
            )
            val recent = Instant.now().toString()
            // N+1 제거: 도구가 listRecentForCategories 로 단일 호출해 categoryId→summaries 맵을 받는다.
            every {
                clippingService.listRecentForCategories(
                    categoryIds = listOf("c1", "c2"),
                    sinceDays = 1,
                    limitPerCategory = 5,
                )
            } returns mapOf(
                "c1" to listOf(summaryInfo("a1", "c1", recent)),
                "c2" to listOf(summaryInfo("b1", "c2", recent)),
            )

            val json = tool.user_get_my_briefing(
                sinceDays = 1, perCategoryLimit = 5, _onBehalfOfUserId = "u1",
            )

            // 구독 카테고리 2개만 sections 에 포함되고, 비구독 c3 은 제외된다.
            json shouldContain "\"categoryId\":\"c1\""
            json shouldContain "\"categoryId\":\"c2\""
            json shouldNotContain "\"categoryId\":\"c3\""
            // 각 section 에 해당 카테고리의 요약만 포함되는지 확인.
            json shouldContain "\"id\":\"a1\""
            json shouldContain "\"id\":\"b1\""
            // 빈 구독 안내는 없음.
            json shouldContain "\"emptyNote\":null"
            // N+1 방지: categoryIds 리스트로 한 번만 호출돼야 한다 (개별 카테고리 호출 0회).
            
            verify(exactly = 1) {
                clippingService.listRecentForCategories(
                    categoryIds = listOf("c1", "c2"),
                    sinceDays = 1,
                    limitPerCategory = 5,
                )
            }
        }

        @Test
        fun `categoryId 가 응답 맵에 없으면 빈 summaries 로 section 을 채운다`() {
            // window 안에 요약이 없는 카테고리는 listRecentForCategories 반환 맵에 키 자체가 빠진다.
            // 도구는 orEmpty() 폴백으로 빈 리스트를 채워 section 자체는 유지해야 한다.
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userOwnedCategoryStore.listCategoryIds("u1") } returns listOf("c1", "c2")
            every { categoryStore.listByIds(setOf("c1", "c2")) } returns listOf(
                Category(id = "c1", name = "AI"),
                Category(id = "c2", name = "Empty"),
            )
            val recent = Instant.now().toString()
            every {
                clippingService.listRecentForCategories(
                    categoryIds = listOf("c1", "c2"),
                    sinceDays = 1,
                    limitPerCategory = 5,
                )
            } returns mapOf(
                // c2 는 키가 없음 — 서비스 규약: window 안에 결과 없는 카테고리는 키 누락.
                "c1" to listOf(summaryInfo("a1", "c1", recent)),
            )

            val json = tool.user_get_my_briefing(
                sinceDays = 1, perCategoryLimit = 5, _onBehalfOfUserId = "u1",
            )

            json shouldContain "\"categoryId\":\"c1\""
            json shouldContain "\"categoryId\":\"c2\""
            // c2 section 은 존재하지만 summaries 는 빈 배열.
            json shouldContain "\"categoryName\":\"Empty\",\"summaries\":[]"
        }

        @Test
        fun `구독이 없으면 emptyNote 를 담은 빈 sections 를 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userOwnedCategoryStore.listCategoryIds("u1") } returns emptyList()

            val json = tool.user_get_my_briefing(
                sinceDays = 1, perCategoryLimit = 5, _onBehalfOfUserId = "u1",
            )

            json shouldContain "\"sections\":[]"
            json shouldContain "구독 중인 카테고리가 없습니다"
            verify(exactly = 0) { categoryStore.listByIds(any()) }
            verify(exactly = 0) { clippingService.listRecentForCategories(any(), any(), any()) }
        }

        @Test
        fun `onBehalfOfUserId 누락 시 InvalidInput 에러 JSON`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.user_get_my_briefing(
                sinceDays = 1, perCategoryLimit = 5, _onBehalfOfUserId = null,
            )

            json shouldContain "\"error\""
            json shouldContain "Caller user id is not bound"
            verify(exactly = 0) { userOwnedCategoryStore.listCategoryIds(any()) }
        }

        @Test
        fun `sinceDays 범위 밖이면 validation 에러`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val tooLow = tool.user_get_my_briefing(
                sinceDays = 0, perCategoryLimit = 5, _onBehalfOfUserId = "u1",
            )
            val tooHigh = tool.user_get_my_briefing(
                sinceDays = 31, perCategoryLimit = 5, _onBehalfOfUserId = "u1",
            )

            tooLow shouldContain "\"error\""
            tooHigh shouldContain "\"error\""
        }

        @Test
        fun `perCategoryLimit 범위 밖이면 validation 에러`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val tooHigh = tool.user_get_my_briefing(
                sinceDays = 1, perCategoryLimit = 11, _onBehalfOfUserId = "u1",
            )

            tooHigh shouldContain "\"error\""
            tooHigh shouldContain "perCategoryLimit"
        }
    }
}
