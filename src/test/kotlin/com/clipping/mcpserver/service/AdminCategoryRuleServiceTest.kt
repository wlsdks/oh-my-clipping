package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.EntityRevisionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminCategoryRuleServiceTest {

    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val entityRevisionStore = mockk<EntityRevisionStore>(relaxed = true)
    private val entityRevisionRecorder = EntityRevisionRecorder(entityRevisionStore)

    private lateinit var service: AdminCategoryRuleService

    private val categoryId = "cat-1"
    private val category = Category(id = categoryId, name = "AI/테크")

    @BeforeEach
    fun setUp() {
        service = AdminCategoryRuleService(categoryStore, categoryRuleStore, entityRevisionRecorder)
        every { categoryStore.findById(categoryId) } returns category
    }

    @Nested
    inner class GetCategoryRule {

        @Test
        fun `저장된 규칙이 있으면 그대로 반환한다`() {
            val storedRule = CategoryRule(
                categoryId = categoryId,
                includeThreshold = 0.7,
                reviewThreshold = 0.4,
                revision = 3
            )
            every { categoryRuleStore.findByCategoryId(categoryId) } returns storedRule

            val result = service.getCategoryRule(categoryId)

            result.includeThreshold shouldBe 0.7
            result.revision shouldBe 3
        }

        @Test
        fun `저장된 규칙이 없으면 기본값을 반환한다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null

            val result = service.getCategoryRule(categoryId)

            result.includeThreshold shouldBe 0.55
            result.reviewThreshold shouldBe 0.35
            result.revision shouldBe 0
        }

        @Test
        fun `존재하지 않는 카테고리면 NotFoundException을 발생시킨다`() {
            every { categoryStore.findById("unknown") } returns null

            shouldThrow<NotFoundException> {
                service.getCategoryRule("unknown")
            }.message shouldBe "Category not found: unknown"
        }
    }

    @Nested
    inner class UpdateCategoryRule {

        @Test
        fun `includeThreshold가 0~1 범위를 벗어나면 예외를 발생시킨다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null

            shouldThrow<InvalidInputException> {
                service.updateCategoryRule(
                    categoryId = categoryId,
                    includeKeywords = null, excludeKeywords = null, riskTags = null,
                    includeThreshold = 1.5, reviewThreshold = null,
                    uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
                )
            }.message shouldBe "includeThreshold must be between 0 and 1"
        }

        @Test
        fun `reviewThreshold만 단독으로 0~1 범위를 벗어나면 예외를 발생시킨다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null

            shouldThrow<InvalidInputException> {
                service.updateCategoryRule(
                    categoryId = categoryId,
                    includeKeywords = null, excludeKeywords = null, riskTags = null,
                    includeThreshold = null, reviewThreshold = -0.1,
                    uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
                )
            }.message shouldBe "reviewThreshold must be between 0 and 1"
        }

        @Test
        fun `includeThreshold가 reviewThreshold보다 작으면 예외를 발생시킨다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null

            shouldThrow<InvalidInputException> {
                service.updateCategoryRule(
                    categoryId = categoryId,
                    includeKeywords = null, excludeKeywords = null, riskTags = null,
                    includeThreshold = 0.3, reviewThreshold = 0.5,
                    uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
                )
            }.message shouldBe "includeThreshold must be greater than or equal to reviewThreshold"
        }

        @Test
        fun `includeThreshold와 reviewThreshold가 동일한 경계값이면 정상 저장한다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = 0.5, reviewThreshold = 0.5,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
            )

            upsertSlot.captured.includeThreshold shouldBe 0.5
            upsertSlot.captured.reviewThreshold shouldBe 0.5
        }

        @Test
        fun `키워드는 trim, dedup, 최대 80개로 정규화된다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            val keywords = listOf("  AI  ", "AI", "  ", "ML", "AI")

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = keywords, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
            )

            upsertSlot.captured.includeKeywords shouldContainExactly listOf("AI", "ML")
        }

        @Test
        fun `riskTags도 동일하게 trim, dedup 정규화된다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            val riskTags = listOf("  violence  ", "violence", "  ", "hate")

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = riskTags,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
            )

            upsertSlot.captured.riskTags shouldContainExactly listOf("violence", "hate")
        }

        @Test
        fun `excludeKeywords가 80개를 초과하면 80개로 잘린다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            val keywords = (1..100).map { "keyword-$it" }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = keywords, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
            )

            upsertSlot.captured.excludeKeywords shouldHaveSize 80
        }

        @Test
        fun `키워드가 80개를 초과하면 80개로 잘린다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            val keywords = (1..100).map { "keyword-$it" }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = keywords, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
            )

            upsertSlot.captured.includeKeywords shouldHaveSize 80
        }

        @Test
        fun `updatedBy가 공백이면 null로 정규화된다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = "   "
            )

            upsertSlot.captured.updatedBy.shouldBeNull()
        }

        @Test
        fun `리비전이 1 증가한다`() {
            val current = CategoryRule(categoryId = categoryId, revision = 5)
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = "admin"
            )

            upsertSlot.captured.revision shouldBe 6
        }

        @Test
        fun `null 값은 기존 값을 유지한다`() {
            val current = CategoryRule(
                categoryId = categoryId,
                includeKeywords = listOf("기존키워드"),
                includeThreshold = 0.8,
                reviewThreshold = 0.4,
                revision = 2
            )
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null
            )

            upsertSlot.captured.includeKeywords shouldContainExactly listOf("기존키워드")
            upsertSlot.captured.includeThreshold shouldBe 0.8
        }

        @Test
        fun `deliveryDays, deliveryHour가 null이면 기존 값을 유지한다`() {
            val current = CategoryRule(
                categoryId = categoryId,
                deliveryDays = listOf("MON", "FRI"),
                deliveryHour = 10,
                deliveryPreset = com.clipping.mcpserver.model.DeliveryPreset.CUSTOM
            )
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null,
                deliveryDays = null,
                deliveryHour = null,
                deliveryPreset = null
            )

            upsertSlot.captured.deliveryDays shouldContainExactly listOf("MON", "FRI")
            upsertSlot.captured.deliveryHour shouldBe 10
            upsertSlot.captured.deliveryPreset shouldBe com.clipping.mcpserver.model.DeliveryPreset.CUSTOM
        }

        @Test
        fun `발송 스케줄이 전달되면 규칙에 반영한다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null,
                deliveryDays = listOf("MON", "WED", "FRI"),
                deliveryHour = 10,
                deliveryPreset = "CUSTOM"
            )

            upsertSlot.captured.deliveryDays shouldContainExactly listOf("MON", "WED", "FRI")
            upsertSlot.captured.deliveryHour shouldBe 10
            upsertSlot.captured.deliveryPreset shouldBe com.clipping.mcpserver.model.DeliveryPreset.CUSTOM
        }

        @Test
        fun `유효하지 않은 deliveryPreset은 기존 값을 유지한다`() {
            val current = CategoryRule(
                categoryId = categoryId,
                deliveryPreset = com.clipping.mcpserver.model.DeliveryPreset.WEEKDAYS
            )
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null,
                deliveryPreset = "INVALID_VALUE"
            )

            upsertSlot.captured.deliveryPreset shouldBe com.clipping.mcpserver.model.DeliveryPreset.WEEKDAYS
        }
    }

    @Nested
    inner class ExcludeEventTypes {

        @Test
        fun `excludeEventTypes가 null이면 기존 값을 유지한다`() {
            // null 은 "해당 필드 수정 안 함" 의 의미. 저장된 current 값이 그대로 유지돼야 한다.
            val current = CategoryRule(
                categoryId = categoryId,
                excludeEventTypes = listOf("OTHER", "FUNDING"),
                revision = 2
            )
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null,
                excludeEventTypes = null
            )

            upsertSlot.captured.excludeEventTypes shouldContainExactly listOf("OTHER", "FUNDING")
        }

        @Test
        fun `excludeEventTypes가 빈 리스트면 룰 비활성 상태로 저장된다`() {
            // 명시적 빈 리스트 → 기존 값이 있어도 비워서 룰 비활성화.
            val current = CategoryRule(
                categoryId = categoryId,
                excludeEventTypes = listOf("OTHER"),
                revision = 1
            )
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null,
                excludeEventTypes = emptyList()
            )

            upsertSlot.captured.excludeEventTypes shouldBe emptyList()
        }

        @Test
        fun `excludeEventTypes는 trim, uppercase, blank 제거, dedup 정규화된다`() {
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            // 다양한 변형이 단일 정규 형태로 수렴해야 한다
            val input = listOf("other", " OTHER ", "FUNDING", "", "  ", "funding")

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null,
                excludeEventTypes = input
            )

            upsertSlot.captured.excludeEventTypes shouldContainExactly listOf("OTHER", "FUNDING")
        }

        @Test
        fun `unknown event_type 값도 정규화만 거쳐 통과된다`() {
            // evaluator 가 unknown 값을 안전하게 무시하므로 서비스는 enum 검증을 하지 않는다.
            every { categoryRuleStore.findByCategoryId(categoryId) } returns null
            val upsertSlot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(upsertSlot)) } answers { upsertSlot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = null,
                excludeEventTypes = listOf("CUSTOM_TYPE", "newly_coined_event")
            )

            upsertSlot.captured.excludeEventTypes shouldContainExactly
                listOf("CUSTOM_TYPE", "NEWLY_COINED_EVENT")
        }
    }

    @Nested
    inner class `낙관적 잠금` {

        @Test
        fun `expectedUpdatedAt이 일치하면 updateWithExpectedUpdatedAt 경로로 저장한다`() {
            val updatedAt = java.time.Instant.parse("2026-04-10T00:00:00Z")
            val current = CategoryRule(categoryId = categoryId, revision = 7, updatedAt = updatedAt)
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val slot = slot<CategoryRule>()
            every {
                categoryRuleStore.updateWithExpectedUpdatedAt(capture(slot), updatedAt)
            } answers { slot.captured.copy(revision = slot.captured.revision + 1) }

            val result = service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = listOf("ai"),
                excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = "admin",
                expectedUpdatedAt = updatedAt
            )

            // store에 전달된 rule의 revision은 service에서 +1 하지 않고 store가 담당 (store가 revision+1 저장).
            slot.captured.revision shouldBe 7
            result.revision shouldBe 8
            verify(exactly = 0) { categoryRuleStore.upsert(any()) }
        }

        @Test
        fun `store가 null을 반환하면 StaleEditInfo를 담은 ConflictException을 던진다`() {
            val stale = java.time.Instant.parse("2026-04-09T00:00:00Z")
            val latestUpdatedAt = java.time.Instant.parse("2026-04-10T00:00:00Z")
            val current = CategoryRule(
                categoryId = categoryId, revision = 4, updatedAt = stale,
                includeKeywords = listOf("old")
            )
            val latest = current.copy(updatedAt = latestUpdatedAt, updatedBy = "other-admin")
            every { categoryRuleStore.findByCategoryId(categoryId) } returnsMany listOf(current, latest)
            every {
                categoryRuleStore.updateWithExpectedUpdatedAt(any(), stale)
            } returns null

            val ex = shouldThrow<com.clipping.mcpserver.error.ConflictException> {
                service.updateCategoryRule(
                    categoryId = categoryId,
                    includeKeywords = listOf("new"),
                    excludeKeywords = null, riskTags = null,
                    includeThreshold = null, reviewThreshold = null,
                    uncertainToReview = null, autoExcludeEnabled = null, updatedBy = "me",
                    expectedUpdatedAt = stale
                )
            }

            ex.staleEditInfo?.latestUpdatedAt shouldBe latestUpdatedAt
            ex.staleEditInfo?.latestEditorName shouldBe "other-admin"
            ex.staleEditInfo?.code shouldBe "STALE_EDIT"
            ex.staleEditInfo?.changedFieldNames shouldBe listOf("includeKeywords")
        }

        @Test
        fun `expectedUpdatedAt이 null이면 upsert 경로로 저장하고 revision을 1 증가시킨다`() {
            val current = CategoryRule(categoryId = categoryId, revision = 2)
            every { categoryRuleStore.findByCategoryId(categoryId) } returns current
            val slot = slot<CategoryRule>()
            every { categoryRuleStore.upsert(capture(slot)) } answers { slot.captured }

            service.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null, excludeKeywords = null, riskTags = null,
                includeThreshold = null, reviewThreshold = null,
                uncertainToReview = null, autoExcludeEnabled = null, updatedBy = "admin",
                expectedUpdatedAt = null
            )

            slot.captured.revision shouldBe 3
            verify(exactly = 0) { categoryRuleStore.updateWithExpectedUpdatedAt(any(), any()) }
        }
    }
}
