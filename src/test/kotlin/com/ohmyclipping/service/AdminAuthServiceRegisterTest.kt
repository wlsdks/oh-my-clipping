package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.config.SecurityProperties
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.Department
import com.ohmyclipping.model.Team
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * V129: [AdminAuthService.registerUser] 가 FK 해석과 legacy 이름 캐시 동기화를
 * 정확히 수행하는지 검증한다. Slack 통지 문자열 회귀 방지는
 * [AdminAuthServiceSlackNotificationTest] 에서 별도로 고정한다.
 */
class AdminAuthServiceRegisterTest {

    private val encoder = BCryptPasswordEncoder()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val departmentTreeService = mockk<DepartmentTreeService>()

    private fun securityProps() = SecurityProperties(
        adminToken = "",
        allowSignup = false,
        allowUserSignup = true,
        allowBootstrapSignup = true,
        minPasswordLength = 10
    )

    private fun service(store: AdminUserStore) = AdminAuthService(
        adminUserStore = store,
        securityProperties = securityProps(),
        passwordEncoder = encoder,
        auditLogStore = auditLogStore,
        operationsNotificationService = operationsNotificationService,
        departmentTreeService = departmentTreeService
    )

    @Nested
    inner class `Signup 경로 registerUser` {
        @Test
        fun `departmentId 와 teamId 를 해석해 FK 와 legacy 이름 캐시를 모두 저장한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            every { store.findByUsername("alice") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }

            every { departmentTreeService.resolveUserAssignment("dept-1", "team-1") } returns
                Pair(
                    Department(id = "dept-1", name = "영업팀", nameNormalized = "영업팀"),
                    Team(id = "team-1", departmentId = "dept-1", name = "퍼포먼스", nameNormalized = "퍼포먼스")
                )

            val created = service(store).registerUser(
                username = "alice",
                displayName = "앨리스",
                departmentId = "dept-1",
                teamId = "team-1",
                rawPassword = "StrongPass123!"
            )

            // FK 쌍 저장 확인.
            savedSlot.captured.departmentId shouldBe "dept-1"
            savedSlot.captured.teamId shouldBe "team-1"
            // legacy name 캐시가 JOIN 결과로 동기화됐는지 확인.
            savedSlot.captured.department shouldBe "영업팀"
            savedSlot.captured.team shouldBe "퍼포먼스"
            created.department shouldBe "영업팀"
        }

        @Test
        fun `teamId 없이 departmentId 만 있으면 team 은 null 이 된다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            every { store.findByUsername("bob") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { departmentTreeService.resolveUserAssignment("dept-1", null) } returns
                (Department(id = "dept-1", name = "개발팀", nameNormalized = "개발팀") to null)

            service(store).registerUser(
                username = "bob",
                displayName = null,
                departmentId = "dept-1",
                rawPassword = "StrongPass123!"
            )

            savedSlot.captured.departmentId shouldBe "dept-1"
            savedSlot.captured.teamId shouldBe null
            savedSlot.captured.team shouldBe null
            savedSlot.captured.department shouldBe "개발팀"
        }
    }

    @Nested
    inner class `Admin-creates-user 경로` {
        @Test
        fun `동일 registerUser 경로를 타므로 FK 저장과 legacy 캐시 sync 가 일관된다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            every { store.findByUsername("carol") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { departmentTreeService.resolveUserAssignment("dept-2", null) } returns
                (Department(id = "dept-2", name = "데이터팀", nameNormalized = "데이터팀") to null)

            // AdminAuthController /user/signup 경로가 동일 registerUser 를 호출한다.
            service(store).registerUser(
                username = "carol",
                displayName = "캐롤",
                departmentId = "dept-2",
                teamId = null,
                rawPassword = "StrongPass123!",
                slackDmChannelId = "D-carol"
            )

            savedSlot.captured.department shouldBe "데이터팀"
            savedSlot.captured.departmentId shouldBe "dept-2"
            savedSlot.captured.slackDmChannelId shouldBe "D-carol"
        }
    }
}
