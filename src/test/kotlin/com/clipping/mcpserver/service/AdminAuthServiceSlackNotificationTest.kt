package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.OpsRequestNotificationEvent

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import com.clipping.mcpserver.config.SecurityProperties
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.Department
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * V129 회귀 방지 — [AdminAuthService.registerUser] 가 legacy `admin_users.department`
 * 이름 캐시를 동기화해 Slack 통지(AdminAuthService.kt:177) 에 "부서 미입력" 이 뜨지 않도록 한다.
 *
 * FK 전환 이후에도 기존 Slack 포매팅이 동일 문구를 유지하는지 고정한다.
 */
class AdminAuthServiceSlackNotificationTest {

    private val encoder = BCryptPasswordEncoder()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val departmentTreeService = mockk<DepartmentTreeService>()

    @Test
    fun `새 가입 요청 Slack 메시지에 부서 이름이 포함되고 '부서 미입력' 회귀가 없다`() {
        val store = mockk<AdminUserStore>()
        every { store.countByRole(AccountRole.ADMIN) } returns 1
        every { store.findByUsername("alice") } returns null
        val savedSlot = slot<AdminUser>()
        every { store.save(capture(savedSlot)) } answers { savedSlot.captured }

        every { departmentTreeService.resolveUserAssignment("dept-1", null) } returns
            (Department(id = "dept-1", name = "영업팀", nameNormalized = "영업팀") to null)

        val service = AdminAuthService(
            adminUserStore = store,
            securityProperties = SecurityProperties(
                adminToken = "",
                allowSignup = false,
                allowUserSignup = true,
                allowBootstrapSignup = true,
                minPasswordLength = 10
            ),
            passwordEncoder = encoder,
            auditLogStore = auditLogStore,
            operationsNotificationService = operationsNotificationService,
            departmentTreeService = departmentTreeService
        )

        service.registerUser(
            username = "alice",
            displayName = "앨리스",
            departmentId = "dept-1",
            rawPassword = "StrongPass123!"
        )

        // OperationsNotificationService 로 전송된 메시지의 body 를 캡처한다.
        val messageSlot = slot<String>()
        verify(exactly = 1) {
            operationsNotificationService.sendOpsRequest(
                OpsRequestNotificationEvent.USER_SIGNUP_REQUESTED,
                capture(messageSlot)
            )
        }

        // legacy department 캐시가 동기화돼 Slack 통지에 "영업팀" 이 노출된다.
        messageSlot.captured shouldContain "영업팀"
        // FK 전환으로 인해 "부서 미입력" 으로 회귀하면 안 된다.
        messageSlot.captured shouldNotContain "부서 미입력"
    }
}
