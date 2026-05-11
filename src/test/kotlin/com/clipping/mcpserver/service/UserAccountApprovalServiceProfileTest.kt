package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.Department
import com.clipping.mcpserver.model.Team
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * V129: [UserAccountApprovalService.updateSelfProfile] 의 FK 저장, 팀-부서 일관성 검증,
 * legacy 이름 캐시 동기화를 집중적으로 검증한다.
 */
class UserAccountApprovalServiceProfileTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val userClippingRequestStore = mockk<UserClippingRequestStore>(relaxed = true)
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>(relaxed = true)
    private val slackMessageSender = mockk<SlackMessageSender>(relaxed = true)
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val categoryStore = mockk<CategoryStore>(relaxed = true)
    private val auditActorResolver = mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
    private val departmentTreeService = mockk<DepartmentTreeService>()
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

    private val baseUser = AdminUser(
        id = "user-1",
        username = "alice",
        passwordHash = "hash",
        role = AccountRole.USER,
        approvalStatus = AccountApprovalStatus.APPROVED,
        isActive = true,
        department = "기존부서",
        departmentId = "dept-old",
        team = null,
        teamId = null
    )

    @Nested
    inner class `FK 쓰기 경로` {
        @Test
        fun `departmentId 와 teamId 를 저장하고 legacy 이름 캐시도 동기화한다`() {
            every { adminUserStore.findByUsername("alice") } returns baseUser
            every { departmentTreeService.resolveUserAssignment("dept-new", "team-new") } returns
                Pair(
                    Department(id = "dept-new", name = "마케팅", nameNormalized = "마케팅"),
                    Team(id = "team-new", departmentId = "dept-new", name = "성장", nameNormalized = "성장")
                )
            val slotUser = slot<AdminUser>()
            every { adminUserStore.update(capture(slotUser)) } answers { firstArg() }

            val result = service.updateSelfProfile(
                username = "alice",
                departmentId = "dept-new",
                teamId = "team-new"
            )

            slotUser.captured.departmentId shouldBe "dept-new"
            slotUser.captured.teamId shouldBe "team-new"
            // Legacy 캐시는 JOIN 된 이름 그대로 반영된다.
            slotUser.captured.department shouldBe "마케팅"
            slotUser.captured.team shouldBe "성장"
            result.departmentId shouldBe "dept-new"
        }

        @Test
        fun `변경 없음이면 store 호출이 생략된다`() {
            every { adminUserStore.findByUsername("alice") } returns baseUser

            val result = service.updateSelfProfile(username = "alice", departmentId = null, teamId = null)

            result shouldBe baseUser
            verify(exactly = 0) { adminUserStore.update(any()) }
        }
    }

    @Nested
    inner class `팀-부서 일관성 검증` {
        @Test
        fun `팀의 departmentId 가 다른 부서면 ConflictException`() {
            every { adminUserStore.findByUsername("alice") } returns baseUser
            every { departmentTreeService.resolveUserAssignment("dept-new", "team-mismatch") } throws
                ConflictException("선택한 팀이 부서에 속하지 않습니다.")

            shouldThrow<ConflictException> {
                service.updateSelfProfile(
                    username = "alice",
                    departmentId = "dept-new",
                    teamId = "team-mismatch"
                )
            }
        }

        @Test
        fun `존재하지 않는 departmentId 면 NotFoundException`() {
            every { adminUserStore.findByUsername("alice") } returns baseUser
            every { departmentTreeService.resolveUserAssignment("ghost", null) } throws
                NotFoundException("부서를 찾을 수 없습니다: ghost")

            shouldThrow<NotFoundException> {
                service.updateSelfProfile(username = "alice", departmentId = "ghost", teamId = null)
            }
        }
    }

    @Nested
    inner class `빈 문자열 입력 처리` {
        @Test
        fun `departmentId 빈 문자열은 부서와 팀을 모두 null 로 초기화한다`() {
            every { adminUserStore.findByUsername("alice") } returns baseUser.copy(teamId = "t1", team = "t")
            every { departmentTreeService.resolveUserAssignment(null, null) } returns (null to null)
            val slotUser = slot<AdminUser>()
            every { adminUserStore.update(capture(slotUser)) } answers { firstArg() }

            service.updateSelfProfile(username = "alice", departmentId = "", teamId = "ignored")

            slotUser.captured.departmentId shouldBe null
            slotUser.captured.teamId shouldBe null
            slotUser.captured.department shouldBe null
            slotUser.captured.team shouldBe null
        }
    }
}
