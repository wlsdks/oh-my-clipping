package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import io.mockk.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class UserAccountApprovalServiceBulkTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val userClippingRequestStore = mockk<UserClippingRequestStore>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
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
    private val service = UserAccountApprovalService(
        adminUserStore,
        userClippingRequestStore,
        auditLogStore,
        BCryptPasswordEncoder(),
        runtimeSettingService,
        slackMessageSender,
        operationsNotificationService,
        categoryStore,
        auditActorResolver,
        departmentTreeService
    )

    private val adminUser = AdminUser(
        id = "admin-1", username = "admin@test.com", passwordHash = "hash",
        role = AccountRole.ADMIN, isActive = true,
        approvalStatus = AccountApprovalStatus.APPROVED
    )
    private fun pendingUser(id: String) = AdminUser(
        id = id, username = "user-$id@test.com", passwordHash = "hash",
        role = AccountRole.USER, isActive = false,
        approvalStatus = AccountApprovalStatus.PENDING
    )
    private fun approvedUser(id: String) = AdminUser(
        id = id, username = "user-$id@test.com", passwordHash = "hash",
        role = AccountRole.USER, isActive = true,
        approvalStatus = AccountApprovalStatus.APPROVED
    )

    @Nested
    inner class `일괄 승인` {
        @Test
        fun `3건 일괄 승인 - 전체 성공`() {
            every { adminUserStore.findByUsername("admin@test.com") } returns adminUser
            // findByIds로 일괄 조회
            every { adminUserStore.findByIds(listOf("u1", "u2", "u3")) } returns listOf(
                pendingUser("u1"), pendingUser("u2"), pendingUser("u3")
            )
            every { adminUserStore.update(any()) } answers { firstArg() }

            val result = service.bulkApproveUserAccounts(
                ids = listOf("u1", "u2", "u3"),
                reviewerUsername = "admin@test.com",
                reviewNote = "확인 완료"
            )

            result.succeeded shouldHaveSize 3
            result.failed shouldHaveSize 0
        }

        @Test
        fun `이미 승인된 건 포함 시 부분 실패`() {
            every { adminUserStore.findByUsername("admin@test.com") } returns adminUser
            // findByIds로 일괄 조회 — u2는 이미 APPROVED 상태
            every { adminUserStore.findByIds(listOf("u1", "u2")) } returns listOf(
                pendingUser("u1"), approvedUser("u2")
            )
            every { adminUserStore.update(any()) } answers { firstArg() }

            val result = service.bulkApproveUserAccounts(
                ids = listOf("u1", "u2"),
                reviewerUsername = "admin@test.com",
                reviewNote = null
            )

            result.succeeded shouldBe listOf("u1")
            result.failed shouldHaveSize 1
            result.failed[0].id shouldBe "u2"
        }

        @Test
        fun `존재하지 않는 유저 포함 시 부분 실패`() {
            every { adminUserStore.findByUsername("admin@test.com") } returns adminUser
            // findByIds — u2는 존재하지 않아 결과에 포함되지 않음
            every { adminUserStore.findByIds(listOf("u1", "u2")) } returns listOf(
                pendingUser("u1")
            )
            every { adminUserStore.update(any()) } answers { firstArg() }

            val result = service.bulkApproveUserAccounts(
                ids = listOf("u1", "u2"),
                reviewerUsername = "admin@test.com",
                reviewNote = null
            )

            result.succeeded shouldBe listOf("u1")
            result.failed shouldHaveSize 1
            result.failed[0].id shouldBe "u2"
        }
    }

    @Nested
    inner class `일괄 반려` {
        @Test
        fun `2건 일괄 반려 - 전체 성공`() {
            every { adminUserStore.findByUsername("admin@test.com") } returns adminUser
            // findByIds로 일괄 조회
            every { adminUserStore.findByIds(listOf("u1", "u2")) } returns listOf(
                pendingUser("u1"), pendingUser("u2")
            )
            every { adminUserStore.update(any()) } answers { firstArg() }

            val result = service.bulkRejectUserAccounts(
                ids = listOf("u1", "u2"),
                reviewerUsername = "admin@test.com",
                reviewNote = "부서 정보 불명확"
            )

            result.succeeded shouldHaveSize 2
            result.failed shouldHaveSize 0
        }

        @Test
        fun `반려 사유 없이 일괄 반려 시 예외 발생`() {
            every { adminUserStore.findByUsername("admin@test.com") } returns adminUser

            val thrown = runCatching {
                service.bulkRejectUserAccounts(
                    ids = listOf("u1"),
                    reviewerUsername = "admin@test.com",
                    reviewNote = null
                )
            }

            thrown.isFailure shouldBe true
        }
    }
}
