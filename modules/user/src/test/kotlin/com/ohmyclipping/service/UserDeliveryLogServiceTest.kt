package com.ohmyclipping.service

import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.UserClippingRequestStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class UserDeliveryLogServiceTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val userClippingRequestStore = mockk<UserClippingRequestStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>()

    private val service = UserDeliveryLogService(
        adminUserStore = adminUserStore,
        userClippingRequestStore = userClippingRequestStore,
        deliveryLogStore = deliveryLogStore
    )

    @Nested
    inner class `getDeliveryLogs 메서드` {

        @Test
        fun `존재하지 않는 사용자이면 NotFoundException을 던진다`() {
            every { adminUserStore.findByUsername("unknown") } returns null

            shouldThrow<NotFoundException> {
                service.getDeliveryLogs("unknown", 7)
            }
        }

        @Test
        fun `USER 역할이 아닌 계정이면 NotFoundException을 던진다`() {
            every { adminUserStore.findByUsername("admin-user") } returns testUser(
                username = "admin-user",
                role = AccountRole.ADMIN
            )

            shouldThrow<NotFoundException> {
                service.getDeliveryLogs("admin-user", 7)
            }
        }

        @Test
        fun `승인된 구독이 없으면 빈 목록을 반환한다`() {
            every { adminUserStore.findByUsername("user1") } returns testUser("user1")
            every { userClippingRequestStore.listByRequesterUserId("user-id-1") } returns listOf(
                testRequest(status = UserClippingRequestStatus.PENDING)
            )

            val result = service.getDeliveryLogs("user1", 7)

            result.deliveries.shouldBeEmpty()
            verify(exactly = 0) { deliveryLogStore.findByCategoryIds(any(), any(), any()) }
        }

        @Test
        fun `승인된 구독의 발송 이력을 정상 반환한다`() {
            every { adminUserStore.findByUsername("user1") } returns testUser("user1")
            every { userClippingRequestStore.listByRequesterUserId("user-id-1") } returns listOf(
                testRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-1"
                ),
                testRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-2"
                )
            )

            val entries = listOf(
                DeliveryLogStore.UserDeliveryLogEntry(
                    date = LocalDate.of(2026, 4, 3),
                    categoryId = "cat-1",
                    categoryName = "AI 트렌드",
                    itemCount = 3,
                    status = "SENT",
                    deliveredAt = Instant.parse("2026-04-03T00:00:00Z")
                ),
                DeliveryLogStore.UserDeliveryLogEntry(
                    date = LocalDate.of(2026, 4, 2),
                    categoryId = "cat-2",
                    categoryName = "Tech News",
                    itemCount = 5,
                    status = "SENT",
                    deliveredAt = Instant.parse("2026-04-02T00:00:00Z")
                )
            )
            every {
                deliveryLogStore.findByCategoryIds(
                    match { it.containsAll(listOf("cat-1", "cat-2")) },
                    any(),
                    any()
                )
            } returns entries

            val result = service.getDeliveryLogs("user1", 7)

            result.deliveries shouldHaveSize 2
            result.deliveries[0].categoryName shouldBe "AI 트렌드"
            result.deliveries[0].itemCount shouldBe 3
            result.deliveries[0].status shouldBe "SENT"
            result.deliveries[1].categoryName shouldBe "Tech News"
        }

        @Test
        fun `days 파라미터가 90을 초과하면 90으로 클램핑한다`() {
            every { adminUserStore.findByUsername("user1") } returns testUser("user1")
            every { userClippingRequestStore.listByRequesterUserId("user-id-1") } returns listOf(
                testRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-1"
                )
            )
            every { deliveryLogStore.findByCategoryIds(any(), any(), any()) } returns emptyList()

            service.getDeliveryLogs("user1", 200)

            // 90일로 클램핑되었는지 from 날짜로 검증한다.
            val fromSlot = slot<LocalDate>()
            verify {
                deliveryLogStore.findByCategoryIds(any(), capture(fromSlot), any())
            }
            val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
            fromSlot.captured shouldBe today.minusDays(89)
        }

        @Test
        fun `days 파라미터가 0 이하이면 1로 클램핑한다`() {
            every { adminUserStore.findByUsername("user1") } returns testUser("user1")
            every { userClippingRequestStore.listByRequesterUserId("user-id-1") } returns listOf(
                testRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-1"
                )
            )
            every { deliveryLogStore.findByCategoryIds(any(), any(), any()) } returns emptyList()

            service.getDeliveryLogs("user1", 0)

            // 1일로 클램핑되었는지 from == today인지 검증한다.
            val fromSlot = slot<LocalDate>()
            val toSlot = slot<LocalDate>()
            verify {
                deliveryLogStore.findByCategoryIds(any(), capture(fromSlot), capture(toSlot))
            }
            fromSlot.captured shouldBe toSlot.captured
        }

        @Test
        fun `중복된 카테고리 ID는 제거하여 조회한다`() {
            every { adminUserStore.findByUsername("user1") } returns testUser("user1")
            every { userClippingRequestStore.listByRequesterUserId("user-id-1") } returns listOf(
                testRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-1"
                ),
                testRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-1"
                )
            )
            every { deliveryLogStore.findByCategoryIds(any(), any(), any()) } returns emptyList()

            service.getDeliveryLogs("user1", 7)

            // 카테고리 ID 목록에서 중복이 제거되었는지 검증한다.
            val idsSlot = slot<List<String>>()
            verify {
                deliveryLogStore.findByCategoryIds(capture(idsSlot), any(), any())
            }
            idsSlot.captured shouldHaveSize 1
            idsSlot.captured[0] shouldBe "cat-1"
        }
    }

    // ── test helpers ──

    private fun testUser(
        username: String,
        role: AccountRole = AccountRole.USER
    ) = AdminUser(
        id = "user-id-1",
        username = username,
        passwordHash = "hash",
        role = role,
        approvalStatus = AccountApprovalStatus.APPROVED,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun testRequest(
        status: UserClippingRequestStatus = UserClippingRequestStatus.PENDING,
        approvedCategoryId: String? = null
    ) = UserClippingRequest(
        id = "req-${System.nanoTime()}",
        requesterUserId = "user-id-1",
        requestName = "테스트 요청",
        sourceName = "테스트 소스",
        sourceUrl = "https://example.com/rss",
        slackChannelId = "C123",
        personaName = "기본 페르소나",
        personaPrompt = "뉴스를 요약해 주세요",
        status = status,
        approvedCategoryId = approvedCategoryId
    )
}
