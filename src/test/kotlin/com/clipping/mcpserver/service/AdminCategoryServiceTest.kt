package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryPurpose
import com.clipping.mcpserver.model.CategoryStatus
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import org.junit.jupiter.api.Nested
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class AdminCategoryServiceTest {

    @Test
    fun `findAll should clamp unsafe pagination before delegating to store`() {
        val store = mockk<CategoryStore>()
        every { store.findAll("ai", 0, 100) } returns emptyList()
        val service = AdminCategoryService(
            store,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<AuditLogStore>(relaxed = true),
            mockk<UserClippingRequestStore>(relaxed = true),
            EntityRevisionRecorder(mockk(relaxed = true)),
            passthroughAuditActorResolver()
        )

        val result = service.findAll(search = "ai", offset = -1, limit = 10_000)

        result shouldBe emptyList()
        verify(exactly = 1) { store.findAll("ai", 0, 100) }
    }

    @Test
    fun `findAll should preserve valid pagination`() {
        val store = mockk<CategoryStore>()
        every { store.findAll(null, 20, 30) } returns emptyList()
        val service = AdminCategoryService(
            store,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<AuditLogStore>(relaxed = true),
            mockk<UserClippingRequestStore>(relaxed = true),
            EntityRevisionRecorder(mockk(relaxed = true)),
            passthroughAuditActorResolver()
        )

        val result = service.findAll(offset = 20, limit = 30)

        result shouldBe emptyList()
        verify(exactly = 1) { store.findAll(null, 20, 30) }
    }

    @Test
    fun `updateCategory should throw conflict when updatedAt mismatch`() {
        val store = mockk<CategoryStore>()
        val category = category(id = "cat-1", updatedAt = Instant.parse("2026-03-02T00:00:00Z"))

        every { store.findById("cat-1") } returns category
        every { store.findByName("경제") } returns null
        every { store.updateWithExpectedUpdatedAt(any(), any()) } returns null

        val service = AdminCategoryService(store, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk<AuditLogStore>(relaxed = true), mockk<UserClippingRequestStore>(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())
        val exception = shouldThrow<ConflictException> {
            service.updateCategory(
                id = "cat-1",
                name = "경제",
                description = null,
                slackChannelId = null,
                isActive = true,
                isPublic = null,
                maxItems = 5,
                personaId = null,
                expectedUpdatedAt = Instant.parse("2026-03-01T23:59:59Z")
            )
        }

        exception.message shouldBe "카테고리가 다른 관리자에 의해 변경되었습니다. 새로고침 후 다시 저장해주세요."
        verify(exactly = 1) { store.updateWithExpectedUpdatedAt(any(), any()) }
        verify(exactly = 0) { store.update(any()) }
    }

    @Test
    fun `updateCategory should reject duplicate category name`() {
        val store = mockk<CategoryStore>()
        val sourceCategory = category(id = "cat-1", updatedAt = Instant.parse("2026-03-02T00:00:00Z"))
        val targetCategory = category(id = "cat-2", updatedAt = Instant.parse("2026-03-02T00:00:00Z")).copy(name = "중복 이름")

        every { store.findById("cat-1") } returns sourceCategory
        every { store.findByName("중복 이름") } returns targetCategory

        val service = AdminCategoryService(store, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk<AuditLogStore>(relaxed = true), mockk<UserClippingRequestStore>(relaxed = true), EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())
        val exception = shouldThrow<InvalidInputException> {
            service.updateCategory(
                id = "cat-1",
                name = "중복 이름",
                description = null,
                slackChannelId = null,
                isActive = true,
                isPublic = null,
                maxItems = 5,
                personaId = null,
                expectedUpdatedAt = null
            )
        }

        exception.message shouldBe "Category name already exists"
        verify(exactly = 0) { store.update(any()) }
        verify(exactly = 0) { store.updateWithExpectedUpdatedAt(any(), any()) }
    }

    @Nested
    inner class `카테고리 모니터링 통계` {
        private val categoryStore = mockk<CategoryStore>()
        private val requestStore = mockk<UserClippingRequestStore>()
        private val sourceStore = mockk<RssSourceStore>()
        private val summaryStore = mockk<BatchSummaryStore>()
        private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
        private val userClippingRequestStore = mockk<UserClippingRequestStore>(relaxed = true)
        private val service = AdminCategoryService(categoryStore, requestStore, sourceStore, summaryStore, auditLogStore, userClippingRequestStore, EntityRevisionRecorder(mockk(relaxed = true)), passthroughAuditActorResolver())

        @Test
        fun `구독자 수를 카테고리별로 조회한다`() {
            every { requestStore.countApprovedGroupByCategoryId() } returns mapOf(
                "cat-1" to 3, "cat-2" to 1
            )
            every { sourceStore.countErrorByCategoryId(any()) } returns 0
            every { summaryStore.findLatestSentByCategoryId(any()) } returns null

            val result = service.getCategoryStats(listOf("cat-1", "cat-2"))

            result.subscriberCounts["cat-1"] shouldBe 3
            result.subscriberCounts["cat-2"] shouldBe 1
        }

        @Test
        fun `구독자가 없으면 빈 맵을 반환한다`() {
            every { requestStore.countApprovedGroupByCategoryId() } returns emptyMap()
            every { sourceStore.countErrorByCategoryId(any()) } returns 0
            every { summaryStore.findLatestSentByCategoryId(any()) } returns null

            val result = service.getCategoryStats(listOf("cat-1"))
            result.subscriberCounts shouldBe emptyMap()
        }

        @Test
        fun `오류 소스 수를 조회한다`() {
            every { requestStore.countApprovedGroupByCategoryId() } returns emptyMap()
            every { sourceStore.countErrorByCategoryId("cat-1") } returns 2
            every { summaryStore.findLatestSentByCategoryId(any()) } returns null

            val result = service.getCategoryStats(listOf("cat-1"))
            result.errorSourceCounts["cat-1"] shouldBe 2
        }

        @Test
        fun `마지막 발송 시간을 조회한다`() {
            val now = java.time.Instant.now()
            every { requestStore.countApprovedGroupByCategoryId() } returns emptyMap()
            every { sourceStore.countErrorByCategoryId(any()) } returns 0
            every { summaryStore.findLatestSentByCategoryId("cat-1") } returns mockk {
                every { createdAt } returns now
            }

            val result = service.getCategoryStats(listOf("cat-1"))
            result.lastDeliveryAts["cat-1"] shouldBe now
        }

        @Test
        fun `발송 이력이 없으면 맵에 포함되지 않는다`() {
            every { requestStore.countApprovedGroupByCategoryId() } returns emptyMap()
            every { sourceStore.countErrorByCategoryId(any()) } returns 0
            every { summaryStore.findLatestSentByCategoryId(any()) } returns null

            val result = service.getCategoryStats(listOf("cat-1"))
            result.lastDeliveryAts shouldBe emptyMap()
        }
    }

    @Nested
    inner class `일시정지와 재개` {
        private val categoryStore = mockk<CategoryStore>()
        private val service = AdminCategoryService(
            categoryStore,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<AuditLogStore>(relaxed = true),
            mockk<UserClippingRequestStore>(relaxed = true),
            EntityRevisionRecorder(mockk(relaxed = true)),
            passthroughAuditActorResolver()
        )

        @Test
        fun `ACTIVE 카테고리를 pause하면 PAUSED 상태를 반환한다`() {
            val activeCategory = category(id = "cat-1", updatedAt = Instant.parse("2026-03-01T00:00:00Z"))
                .copy(status = CategoryStatus.ACTIVE, isActive = true)
            val pausedCategory = activeCategory.copy(status = CategoryStatus.PAUSED, isActive = false, pausedAt = Instant.parse("2026-03-02T00:00:00Z"))

            every { categoryStore.findById("cat-1") } returnsMany listOf(activeCategory, pausedCategory)
            every { categoryStore.pause("cat-1") } returns true
            every { categoryStore.countSources("cat-1") } returns 0

            val result = service.pauseCategory("cat-1")

            result.status shouldBe CategoryStatus.PAUSED
            verify(exactly = 1) { categoryStore.pause("cat-1") }
        }

        @Test
        fun `이미 PAUSED 상태이면 pause 호출 시 store를 호출하지 않고 멱등으로 반환한다`() {
            val pausedCategory = category(id = "cat-1", updatedAt = Instant.parse("2026-03-01T00:00:00Z"))
                .copy(status = CategoryStatus.PAUSED, isActive = false)

            every { categoryStore.findById("cat-1") } returns pausedCategory

            val result = service.pauseCategory("cat-1")

            result.status shouldBe CategoryStatus.PAUSED
            verify(exactly = 0) { categoryStore.pause(any()) }
        }

        @Test
        fun `PAUSED 카테고리를 resume하면 ACTIVE 상태를 반환한다`() {
            val pausedCategory = category(id = "cat-1", updatedAt = Instant.parse("2026-03-01T00:00:00Z"))
                .copy(status = CategoryStatus.PAUSED, isActive = false, pausedAt = Instant.parse("2026-03-01T12:00:00Z"))
            val activeCategory = pausedCategory.copy(status = CategoryStatus.ACTIVE, isActive = true, pausedAt = null)

            every { categoryStore.findById("cat-1") } returnsMany listOf(pausedCategory, activeCategory)
            every { categoryStore.resume("cat-1") } returns true
            every { categoryStore.countSources("cat-1") } returns 0

            val result = service.resumeCategory("cat-1")

            result.status shouldBe CategoryStatus.ACTIVE
            verify(exactly = 1) { categoryStore.resume("cat-1") }
        }

        @Test
        fun `이미 ACTIVE 상태이면 resume 호출 시 store를 호출하지 않고 멱등으로 반환한다`() {
            val activeCategory = category(id = "cat-1", updatedAt = Instant.parse("2026-03-01T00:00:00Z"))
                .copy(status = CategoryStatus.ACTIVE, isActive = true)

            every { categoryStore.findById("cat-1") } returns activeCategory

            val result = service.resumeCategory("cat-1")

            result.status shouldBe CategoryStatus.ACTIVE
            verify(exactly = 0) { categoryStore.resume(any()) }
        }
    }

    private fun category(id: String, updatedAt: Instant): Category =
        Category(
            id = id,
            name = "기본 카테고리",
            description = "설명",
            slackChannelId = "C0123ABCD",
            isActive = true,
            maxItems = 5,
            personaId = null,
            createdAt = Instant.parse("2026-03-01T00:00:00Z"),
            updatedAt = updatedAt
        )

    @Nested
    inner class `입력 제약 검증` {

        private val categoryStore = mockk<CategoryStore>()
        private val service = AdminCategoryService(
            categoryStore,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<AuditLogStore>(relaxed = true),
            mockk<UserClippingRequestStore>(relaxed = true),
            EntityRevisionRecorder(mockk(relaxed = true)),
            passthroughAuditActorResolver()
        )

        @Test
        fun `카테고리 이름이 200자 초과면 InvalidInputException을 던진다`() {
            val ex = shouldThrow<InvalidInputException> {
                service.createCategory(
                    name = "a".repeat(201),
                    description = null,
                    slackChannelId = null,
                    maxItems = 5,
                    personaId = null
                )
            }
            ex.message shouldBe "주제 이름은 최대 200자까지 입력할 수 있어요"
        }

        @Test
        fun `카테고리 설명이 1000자 초과면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.createCategory(
                    name = "테스트",
                    description = "a".repeat(1001),
                    slackChannelId = null,
                    maxItems = 5,
                    personaId = null
                )
            }
        }

        @Test
        fun `Slack 채널 URL은 표준 채널 ID로 정규화되어 저장된다`() {
            every { categoryStore.findByName(any()) } returns null
            val captured = slot<Category>()
            every { categoryStore.save(capture(captured)) } answers {
                firstArg<Category>().copy(id = "cat-new")
            }

            service.createCategory(
                name = "주제1",
                description = null,
                slackChannelId = "https://example.slack.com/archives/C0123ABCD",
                maxItems = 5,
                personaId = null
            )

            captured.captured.slackChannelId shouldBe "C0123ABCD"
        }

        @Test
        fun `Slack 채널 접두어 # 입력도 정규화한다`() {
            every { categoryStore.findByName(any()) } returns null
            val captured = slot<Category>()
            every { categoryStore.save(capture(captured)) } answers {
                firstArg<Category>().copy(id = "cat-new")
            }

            service.createCategory(
                name = "주제2",
                description = null,
                slackChannelId = "#C01ABCDEF12",
                maxItems = 5,
                personaId = null
            )

            captured.captured.slackChannelId shouldBe "C01ABCDEF12"
        }

        @Test
        fun `createCategory 에 purpose,background,problem_statement 를 전달하면 store 에 전파된다`() {
            every { categoryStore.findByName(any()) } returns null
            val captured = slot<Category>()
            every { categoryStore.save(capture(captured)) } answers {
                firstArg<Category>().copy(id = "cat-new")
            }

            service.createCategory(
                name = "영업 경쟁사",
                description = null,
                slackChannelId = null,
                maxItems = 5,
                personaId = null,
                purpose = "SALES",
                background = "영업팀 매일 경쟁사 모니터링",
                problemStatement = "수동 검색 시간 소요"
            )

            captured.captured.purpose shouldBe CategoryPurpose.SALES
            captured.captured.background shouldBe "영업팀 매일 경쟁사 모니터링"
            captured.captured.problemStatement shouldBe "수동 검색 시간 소요"
        }

        @Test
        fun `purpose 로 enum 밖 값을 주면 InvalidInputException`() {
            every { categoryStore.findByName(any()) } returns null
            shouldThrow<InvalidInputException> {
                service.createCategory(
                    name = "주제X",
                    description = null,
                    slackChannelId = null,
                    maxItems = 5,
                    personaId = null,
                    purpose = "NOT_A_REAL_PURPOSE"
                )
            }
        }

        @Test
        fun `purpose 가 소문자여도 대문자로 정규화해 enum 매핑된다`() {
            every { categoryStore.findByName(any()) } returns null
            val captured = slot<Category>()
            every { categoryStore.save(capture(captured)) } answers {
                firstArg<Category>().copy(id = "cat-new")
            }

            service.createCategory(
                name = "주제Y",
                description = null,
                slackChannelId = null,
                maxItems = 5,
                personaId = null,
                purpose = "research"
            )

            captured.captured.purpose shouldBe CategoryPurpose.RESEARCH
        }

        @Test
        fun `background 가 2000자 초과면 InvalidInputException`() {
            every { categoryStore.findByName(any()) } returns null
            shouldThrow<InvalidInputException> {
                service.createCategory(
                    name = "주제Z",
                    description = null,
                    slackChannelId = null,
                    maxItems = 5,
                    personaId = null,
                    background = "a".repeat(2001)
                )
            }
        }
    }
}

/** 테스트에서 `AuditActorResolver`를 principal → actorId passthrough 로 동작시키는 헬퍼. */
private fun passthroughAuditActorResolver(): AuditActorResolver =
    mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
