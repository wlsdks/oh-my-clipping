package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.UserClippingRequestStore
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserCategoryBrowseServiceTest {

    private val categoryStore = mockk<CategoryStore>()
    private val requestStore = mockk<UserClippingRequestStore>()
    private val adminUserStore = mockk<AdminUserStore>()
    private val service = UserCategoryBrowseService(categoryStore, requestStore, adminUserStore)

    private val testUser = AdminUser(
        id = "user-1",
        username = "testuser",
        passwordHash = "hashed",
        slackDmChannelId = "D_DM_TEST"
    )

    @Nested
    inner class `browse 메서드` {

        @Test
        fun `비공개 카테고리는 browse 목록에서 제외된다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findPublicOperational() } returns listOf(
                testCategory(id = "cat-public", name = "공개 카테고리", isPublic = true)
            )
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()
            every { requestStore.countApprovedGroupByCategoryId() } returns emptyMap()

            val result = service.browse("testuser")

            result shouldHaveSize 1
            result.single().id shouldBe "cat-public"
            result.single().name shouldBe "공개 카테고리"
            verify(exactly = 1) { categoryStore.findPublicOperational() }
            verify(exactly = 0) { requestStore.listAll(UserClippingRequestStatus.APPROVED) }
        }

        @Test
        fun `이름이 같은 활성 카테고리는 대표 항목 하나만 노출한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findPublicOperational() } returns listOf(
                testCategory(
                    id = "cat-canonical",
                    name = "AI/테크",
                    createdAt = java.time.Instant.parse("2026-03-01T00:00:00Z")
                ),
                testCategory(
                    id = "cat-duplicate",
                    name = "AI/테크",
                    createdAt = java.time.Instant.parse("2026-03-02T00:00:00Z")
                )
            )
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                UserClippingRequest(
                    id = "req-1",
                    requesterUserId = "user-1",
                    requestName = "AI 브리핑",
                    sourceName = "",
                    sourceUrl = "",
                    slackChannelId = "C_TEST",
                    personaName = "",
                    personaPrompt = "",
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-canonical"
                )
            )
            every { requestStore.countApprovedGroupByCategoryId() } returns mapOf("cat-canonical" to 1)

            val result = service.browse("testuser")

            result shouldHaveSize 1
            result.single().id shouldBe "cat-canonical"
            result.single().name shouldBe "AI/테크"
            result.single().isSubscribed shouldBe true
            result.single().subscriberCount shouldBe 1
            verify(exactly = 1) { categoryStore.findPublicOperational() }
            verify(exactly = 0) { requestStore.listAll(UserClippingRequestStatus.APPROVED) }
        }
    }

    @Nested
    inner class `subscribe 메서드` {

        @Test
        fun `존재하지 않는 사용자이면 NotFoundException을 던진다`() {
            every { adminUserStore.findByUsername("unknown") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.subscribe("unknown", "cat-1", "C_SLACK")
            }

            exception.message shouldBe "User not found"
            verify(exactly = 0) { requestStore.save(any()) }
        }

        @Test
        fun `존재하지 않는 카테고리이면 NotFoundException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("missing-cat") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.subscribe("testuser", "missing-cat", "C_SLACK")
            }

            exception.message shouldBe "카테고리를 찾을 수 없습니다"
            verify(exactly = 0) { requestStore.save(any()) }
        }

        @Test
        fun `비활성화된 카테고리이면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-inactive") } returns testCategory("cat-inactive", isActive = false)

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-inactive", "")
            }

            exception.message shouldBe "비활성화된 카테고리는 구독할 수 없습니다."
            verify(exactly = 0) { requestStore.save(any()) }
        }

        @Test
        fun `비공개 카테고리이면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-private") } returns testCategory(
                "cat-private", isPublic = false
            )

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-private", "")
            }

            exception.message shouldBe "비공개 카테고리는 구독할 수 없습니다."
            verify(exactly = 0) { requestStore.save(any()) }
        }

        @Test
        fun `구독 한도 5개를 초과하면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-new") } returns testCategory("cat-new", slackChannelId = "C_CATEGORY")
            stubSubscribeGuards(monthlyCount = 0, activeCount = 5)

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-new", "C_SLACK")
            }

            exception.message shouldBe "구독 한도(5개)에 도달했습니다"
            verify(exactly = 0) { requestStore.save(any()) }
        }

        @Test
        fun `PENDING 상태도 한도 계산에 포함된다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-new") } returns testCategory("cat-new", slackChannelId = "C_CATEGORY")
            // APPROVED 3개 + PENDING 2개 = 5개로 한도 도달.
            stubSubscribeGuards(monthlyCount = 0, activeCount = 5)

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-new", "C_SLACK")
            }

            exception.message shouldBe "구독 한도(5개)에 도달했습니다"
        }

        @Test
        fun `이번 달 5건 초과 시 월간 한도 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-new") } returns testCategory("cat-new", slackChannelId = "C_CATEGORY")
            // 이번 달에 이미 5건의 유효 요청이 존재 — 6번째 생성은 거부돼야 한다.
            stubSubscribeGuards(monthlyCount = 5)

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-new", "C_CATEGORY")
            }

            exception.message shouldBe "이번 달 신규 요청 한도(5건)에 도달했습니다. 다음 달에 다시 시도해 주세요."
        }

        @Test
        fun `WITHDRAWN 요청은 월간 한도에 포함되지 않는다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-new") } returns testCategory("cat-new", slackChannelId = "C_CATEGORY")
            // Store count는 WITHDRAWN/REJECTED를 제외한 유효 요청만 반환한다.
            stubSubscribeGuards(monthlyCount = 1, activeCount = 1)
            val savedSlot = slot<UserClippingRequest>()
            every { requestStore.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = "req-new") }

            val result = service.subscribe("testuser", "cat-new", "C_CATEGORY")

            result.status shouldBe "APPROVED"
        }

        @Test
        fun `이미 구독 중인 카테고리이면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-dup") } returns testCategory("cat-dup", slackChannelId = "C_CATEGORY")
            stubSubscribeGuards(alreadySubscribed = true)

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-dup", "C_SLACK")
            }

            exception.message shouldBe "이미 구독 중인 카테고리입니다"
            verify(exactly = 0) { requestStore.save(any()) }
        }

        @Test
        fun `카테고리 기본 채널이면 APPROVED 상태로 저장한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-ok") } returns testCategory("cat-ok", slackChannelId = "C_CATEGORY")
            stubSubscribeGuards()
            val savedSlot = slot<UserClippingRequest>()
            every { requestStore.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = "req-new-1")
            }

            val result = service.subscribe("testuser", "cat-ok", "C_CATEGORY")

            result.requestId shouldBe "req-new-1"
            result.categoryId shouldBe "cat-ok"
            result.status shouldBe "APPROVED"
            savedSlot.captured.status shouldBe UserClippingRequestStatus.APPROVED
            savedSlot.captured.approvedCategoryId shouldBe "cat-ok"
            savedSlot.captured.slackChannelId shouldBe "C_CATEGORY"
        }

        @Test
        fun `카테고리 기본 채널과 다른 공유 채널이면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-ok") } returns testCategory("cat-ok", slackChannelId = "C_CATEGORY")
            stubSubscribeGuards()

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-ok", "C_OTHER")
            }

            exception.message shouldBe
                "기존 카테고리는 기본 Slack 채널 또는 본인 DM으로만 구독할 수 있습니다."
            verify(exactly = 0) { requestStore.save(any()) }
        }

        @Test
        fun `slackChannelId가 빈 문자열이면 사용자의 DM 채널을 사용한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-dm") } returns testCategory("cat-dm")
            stubSubscribeGuards()
            val savedSlot = slot<UserClippingRequest>()
            every { requestStore.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = "req-dm-1")
            }

            service.subscribe("testuser", "cat-dm", "")

            savedSlot.captured.slackChannelId shouldBe "D_DM_TEST"
        }

        @Test
        fun `slackChannelId가 DM 문자열이면 사용자의 DM 채널을 사용한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { categoryStore.findById("cat-dm") } returns testCategory("cat-dm")
            stubSubscribeGuards()
            val savedSlot = slot<UserClippingRequest>()
            every { requestStore.save(capture(savedSlot)) } answers {
                savedSlot.captured.copy(id = "req-dm-2")
            }

            service.subscribe("testuser", "cat-dm", "DM")

            savedSlot.captured.slackChannelId shouldBe "D_DM_TEST"
        }

        @Test
        fun `빈 slackChannelId와 사용자 DM 부재면 InvalidInputException을 던진다`() {
            val userNoDm = testUser.copy(slackDmChannelId = null)
            every { adminUserStore.findByUsername("testuser") } returns userNoDm
            every { categoryStore.findById("cat-no-dm") } returns testCategory("cat-no-dm")
            stubSubscribeGuards()

            val exception = shouldThrow<InvalidInputException> {
                service.subscribe("testuser", "cat-no-dm", "")
            }

            exception.message shouldBe
                "Slack DM 채널 ID가 설정되지 않았습니다. 프로필에서 설정해 주세요."
            verify(exactly = 0) { requestStore.save(any()) }
        }
    }

    private fun testCategory(
        id: String,
        name: String = "카테고리 $id",
        slackChannelId: String? = null,
        isActive: Boolean = true,
        isPublic: Boolean = true,
        createdAt: java.time.Instant = java.time.Instant.parse("2026-03-01T00:00:00Z")
    ): Category =
        Category(
            id = id,
            name = name,
            slackChannelId = slackChannelId,
            isActive = isActive,
            isPublic = isPublic,
            createdAt = createdAt,
            status = if (isActive) CategoryStatus.ACTIVE else CategoryStatus.PAUSED
        )

    private fun stubSubscribeGuards(
        monthlyCount: Int = 0,
        activeCount: Int = 0,
        alreadySubscribed: Boolean = false
    ) {
        every { requestStore.countCreatedSinceByRequesterUserId(eq("user-1"), any()) } returns monthlyCount
        every { requestStore.countActiveSubscriptionsByRequesterUserId("user-1") } returns activeCount
        every {
            requestStore.existsApprovedByRequesterUserIdAndCategoryId("user-1", any())
        } returns alreadySubscribed
    }
}
