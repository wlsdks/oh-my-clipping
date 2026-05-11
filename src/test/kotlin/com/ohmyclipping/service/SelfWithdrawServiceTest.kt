package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.UserClippingRequestStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant

class SelfWithdrawServiceTest {

    private val adminUserStore = mockk<AdminUserStore>(relaxed = true)
    private val requestStore = mockk<UserClippingRequestStore>(relaxed = true)
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val encoder = BCryptPasswordEncoder()
    private val runtimeSettingService = mockk<RuntimeSettingService>(relaxed = true)
    private val slackMessageSender = mockk<SlackMessageSender>(relaxed = true)
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val categoryStore = mockk<CategoryStore>(relaxed = true)
    /** Principal → actorId passthrough: 테스트에서 `verify { auditLogStore.log(actorId = "admin", ...) }` 가 동작하도록 한다. */
    private val auditActorResolver = mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
    private val departmentTreeService = mockk<DepartmentTreeService>(relaxed = true)
    private val sut = UserAccountApprovalService(
        adminUserStore,
        requestStore,
        auditLogStore,
        encoder,
        runtimeSettingService,
        slackMessageSender,
        operationsNotificationService,
        categoryStore,
        auditActorResolver,
        departmentTreeService
    )
    private val rawPassword = "StrongPass123!"
    private val hashedPassword = encoder.encode(rawPassword)

    private fun makeUser(
        isActive: Boolean = true,
        role: AccountRole = AccountRole.USER,
        approvalStatus: AccountApprovalStatus = AccountApprovalStatus.APPROVED
    ) = AdminUser(
        id = "user-1",
        username = "testuser",
        passwordHash = hashedPassword,
        role = role,
        isActive = isActive,
        approvalStatus = approvalStatus,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Nested
    inner class `셀프 탈퇴` {
        @Test
        fun `정상 탈퇴 시 계정이 비활성화되고 구독이 해제된다`() {
            val user = makeUser()
            every { adminUserStore.findByUsername("testuser") } returns user
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                mockk<UserClippingRequest> {
                    every { id } returns "req-1"
                    every { status } returns UserClippingRequestStatus.APPROVED
                }
            )
            every { adminUserStore.update(any()) } answers { firstArg() }

            val result = sut.selfWithdraw("testuser", rawPassword)

            result.isActive shouldBe false
            result.approvalStatus shouldBe AccountApprovalStatus.REJECTED
            result.slackDmChannelId shouldBe null

            verify(exactly = 1) { requestStore.updateStatusBulk(listOf("req-1"), UserClippingRequestStatus.WITHDRAWN, any(), any()) }
            verify(exactly = 1) { auditLogStore.log(any(), any(), eq("SELF_WITHDRAW"), any(), any(), any(), any()) }
        }

        @Test
        fun `잘못된 비밀번호로 탈퇴 시도 시 예외 발생`() {
            val user = makeUser()
            every { adminUserStore.findByUsername("testuser") } returns user

            shouldThrow<InvalidInputException> {
                sut.selfWithdraw("testuser", "WrongPassword!")
            }
        }

        @Test
        fun `이미 비활성화된 계정은 탈퇴할 수 없다`() {
            val user = makeUser(isActive = false)
            every { adminUserStore.findByUsername("testuser") } returns user

            shouldThrow<InvalidInputException> {
                sut.selfWithdraw("testuser", rawPassword)
            }
        }

        @Test
        fun `존재하지 않는 사용자는 NotFoundException 발생`() {
            every { adminUserStore.findByUsername("ghost") } returns null

            shouldThrow<NotFoundException> {
                sut.selfWithdraw("ghost", rawPassword)
            }
        }

        @Test
        fun `ADMIN 계정은 셀프 탈퇴할 수 없다`() {
            val admin = makeUser(role = AccountRole.ADMIN)
            every { adminUserStore.findByUsername("testuser") } returns admin

            shouldThrow<InvalidInputException> {
                sut.selfWithdraw("testuser", rawPassword)
            }
        }
    }
}
