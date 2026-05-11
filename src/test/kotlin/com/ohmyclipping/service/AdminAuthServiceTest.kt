package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.config.SecurityProperties
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.ErrorCode
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.SignupException
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AdminAuthServiceTest {

    private val passwordEncoder = BCryptPasswordEncoder()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    // V129: FK 해석을 위임받는 서비스. relaxed=true 로 기본은 (null, null) 을 돌려주고 테스트마다 override 한다.
    private val departmentTreeService = mockk<DepartmentTreeService>(relaxed = true).apply {
        every { resolveUserAssignment(any(), any()) } returns (null to null)
    }

    private fun defaultProps(
        allowSignup: Boolean = false,
        allowUserSignup: Boolean = true,
        allowBootstrapSignup: Boolean = true,
        minPasswordLength: Int = 10
    ) = SecurityProperties(
        adminToken = "",
        allowSignup = allowSignup,
        allowUserSignup = allowUserSignup,
        allowBootstrapSignup = allowBootstrapSignup,
        minPasswordLength = minPasswordLength
    )

    @Test
    fun `signup should be allowed for first admin user`() {
        val store = mockk<AdminUserStore>()
        every { store.countByRole(AccountRole.ADMIN) } returns 0

        val service = AdminAuthService(
            adminUserStore = store,
            securityProperties = SecurityProperties(
                adminToken = "",
                allowSignup = false,
                allowUserSignup = true,
                allowBootstrapSignup = true,
                minPasswordLength = 10
            ),
            passwordEncoder = passwordEncoder,
            auditLogStore = auditLogStore,
            operationsNotificationService = operationsNotificationService,
            departmentTreeService = departmentTreeService
        )

        val availability = service.signupAvailability(AccountRole.ADMIN)
        availability.allowed shouldBe true
        availability.reason shouldBe "first_admin_bootstrap"
    }

    @Test
    fun `signup should be blocked when bootstrap done and open signup disabled`() {
        val store = mockk<AdminUserStore>()
        every { store.countByRole(AccountRole.ADMIN) } returns 1

        val service = AdminAuthService(
            adminUserStore = store,
            securityProperties = SecurityProperties(
                adminToken = "",
                allowSignup = false,
                allowUserSignup = true,
                allowBootstrapSignup = true,
                minPasswordLength = 10
            ),
            passwordEncoder = passwordEncoder,
            auditLogStore = auditLogStore,
            operationsNotificationService = operationsNotificationService,
            departmentTreeService = departmentTreeService
        )

        val availability = service.signupAvailability(AccountRole.ADMIN)
        availability.allowed shouldBe false
        availability.reason shouldBe "admin_signup_disabled"
    }

    @Test
    fun `register should hash password and normalize username`() {
        val store = mockk<AdminUserStore>()
        every { store.countByRole(AccountRole.ADMIN) } returns 0
        every { store.findByUsername("admin.team") } returns null
        val savedSlot = slot<AdminUser>()
        every { store.save(capture(savedSlot)) } answers { savedSlot.captured }

        val service = AdminAuthService(
            adminUserStore = store,
            securityProperties = SecurityProperties(
                adminToken = "",
                allowSignup = false,
                allowUserSignup = true,
                allowBootstrapSignup = true,
                minPasswordLength = 10
            ),
            passwordEncoder = passwordEncoder,
            auditLogStore = auditLogStore,
            operationsNotificationService = operationsNotificationService,
            departmentTreeService = departmentTreeService
        )

        val created = service.registerAdmin(
            username = "  Admin.Team  ",
            displayName = "Ops Admin",
            rawPassword = "StrongPass123!"
        )

        created.username shouldBe "admin.team"
        created.displayName shouldBe "Ops Admin"
        (created.passwordHash == "StrongPass123!") shouldBe false
        passwordEncoder.matches("StrongPass123!", created.passwordHash) shouldBe true
        verify(exactly = 1) { store.save(any()) }
    }

    @Test
    fun `register should reject duplicate username`() {
        val store = mockk<AdminUserStore>()
        every { store.countByRole(AccountRole.ADMIN) } returns 0
        every { store.findByUsername("admin") } returns AdminUser(
            id = "u1",
            username = "admin",
            passwordHash = "hashed"
        )

        val service = AdminAuthService(
            adminUserStore = store,
            securityProperties = SecurityProperties(
                adminToken = "",
                allowSignup = true,
                allowUserSignup = true,
                allowBootstrapSignup = true,
                minPasswordLength = 10
            ),
            passwordEncoder = passwordEncoder,
            auditLogStore = auditLogStore,
            operationsNotificationService = operationsNotificationService,
            departmentTreeService = departmentTreeService
        )

        shouldThrow<ConflictException> {
            service.registerAdmin(
                username = "admin",
                displayName = null,
                rawPassword = "StrongPass123!"
            )
        }.message shouldBe "Username already exists"
    }

    @Test
    fun `register should reject short password`() {
        val store = mockk<AdminUserStore>()
        every { store.countByRole(AccountRole.ADMIN) } returns 0
        every { store.findByUsername(any()) } returns null

        val service = AdminAuthService(
            adminUserStore = store,
            securityProperties = SecurityProperties(
                adminToken = "",
                allowSignup = true,
                allowUserSignup = true,
                allowBootstrapSignup = true,
                minPasswordLength = 10
            ),
            passwordEncoder = passwordEncoder,
            auditLogStore = auditLogStore,
            operationsNotificationService = operationsNotificationService,
            departmentTreeService = departmentTreeService
        )

        shouldThrow<SignupException> {
            service.registerAdmin(
                username = "admin",
                displayName = null,
                rawPassword = "short"
            )
        }.message shouldBe "Password must be at least 10 characters"
    }

    @Test
    fun `register user should require department and create pending account`() {
        val store = mockk<AdminUserStore>()
        every { store.countByRole(AccountRole.ADMIN) } returns 1
        every { store.findByUsername("requester.one") } returns null
        val savedSlot = slot<AdminUser>()
        every { store.save(capture(savedSlot)) } answers { savedSlot.captured }

        val service = AdminAuthService(
            adminUserStore = store,
            securityProperties = SecurityProperties(
                adminToken = "",
                allowSignup = false,
                allowUserSignup = true,
                allowBootstrapSignup = true,
                minPasswordLength = 10
            ),
            passwordEncoder = passwordEncoder,
            auditLogStore = auditLogStore,
            operationsNotificationService = operationsNotificationService,
            departmentTreeService = departmentTreeService
        )

        shouldThrow<SignupException> {
            service.registerUser(
                username = "requester.one",
                displayName = "요청자",
                departmentId = " ",
                rawPassword = "StrongPass123!"
            )
        }.errorCode.code shouldBe "invalid_input"

        // V129: departmentTreeService 가 FK 를 해석해 Department 객체를 돌려준다.
        every { departmentTreeService.resolveUserAssignment("dept-platform", null) } returns
            (com.ohmyclipping.model.Department(
                id = "dept-platform",
                name = "플랫폼팀",
                nameNormalized = "플랫폼팀"
            ) to null)

        val created = service.registerUser(
            username = "requester.one",
            displayName = "요청자",
            departmentId = "dept-platform",
            rawPassword = "StrongPass123!"
        )

        created.role shouldBe AccountRole.USER
        // legacy department 이름 캐시는 JOIN 결과(Department.name) 로 저장된다.
        created.department shouldBe "플랫폼팀"
        created.departmentId shouldBe "dept-platform"
        created.approvalStatus shouldBe AccountApprovalStatus.PENDING
        verify(exactly = 1) { store.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateLastLogin
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class UpdateLastLogin {

        @Test
        fun `updateLastLogin은 스토어 위임을 정확히 호출한다`() {
            val store = mockk<AdminUserStore>()
            every { store.updateLastLoginAt("testuser") } just runs
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            service.updateLastLogin("testuser")

            verify(exactly = 1) { store.updateLastLoginAt("testuser") }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // signupAvailability(USER)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class SignupAvailabilityUser {

        @Test
        fun `allowUserSignup=true이면 user_signup_enabled를 반환한다`() {
            val store = mockk<AdminUserStore>()
            val service = AdminAuthService(store, defaultProps(allowUserSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val result = service.signupAvailability(AccountRole.USER)

            result.allowed shouldBe true
            result.reason shouldBe "user_signup_enabled"
        }

        @Test
        fun `allowUserSignup=false이면 user_signup_disabled를 반환한다`() {
            val store = mockk<AdminUserStore>()
            val service = AdminAuthService(store, defaultProps(allowUserSignup = false), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val result = service.signupAvailability(AccountRole.USER)

            result.allowed shouldBe false
            result.reason shouldBe "user_signup_disabled"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // signupAvailability(ADMIN) edge cases
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class SignupAvailabilityAdmin {

        @Test
        fun `allowSignup=true + 관리자 존재하면 admin_signup_enabled를 반환한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            val service = AdminAuthService(store, defaultProps(allowSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val result = service.signupAvailability(AccountRole.ADMIN)

            result.allowed shouldBe true
            result.reason shouldBe "admin_signup_enabled"
        }

        @Test
        fun `allowBootstrapSignup=false + adminCount=0이면 admin_signup_disabled를 반환한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            val service = AdminAuthService(
                store,
                defaultProps(allowSignup = false, allowBootstrapSignup = false),
                passwordEncoder,
                auditLogStore,
                operationsNotificationService,
                departmentTreeService
            )

            val result = service.signupAvailability(AccountRole.ADMIN)

            result.allowed shouldBe false
            result.reason shouldBe "admin_signup_disabled"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // registerAdmin edge cases
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class RegisterAdmin {

        @Test
        fun `회원가입이 비활성화되어 있으면 SignupException을 발생시킨다`() {
            val store = mockk<AdminUserStore>()
            // adminCount=1이어서 bootstrap 불가, allowSignup=false
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            val service = AdminAuthService(store, defaultProps(allowSignup = false), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<SignupException> {
                service.registerAdmin(
                    username = "newadmin",
                    displayName = null,
                    rawPassword = "StrongPass123!"
                )
            }.errorCode.code shouldBe "signup_disabled"
        }

        @Test
        fun `username이 공백이면 SIGNUP_INVALID_INPUT 예외를 발생시킨다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<SignupException> {
                service.registerAdmin(
                    username = "   ",
                    displayName = null,
                    rawPassword = "StrongPass123!"
                )
            }.errorCode shouldBe ErrorCode.SIGNUP_INVALID_INPUT
        }

        @Test
        fun `ADMIN 등록 시 approvedAt이 설정된다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            every { store.findByUsername("newadmin") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val result = service.registerAdmin(
                username = "newadmin",
                displayName = null,
                rawPassword = "StrongPass123!"
            )

            result.approvedAt.shouldNotBeNull()
            result.approvalStatus shouldBe AccountApprovalStatus.APPROVED
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // registerUser edge cases
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class RegisterUser {

        @Test
        fun `USER 등록 시 approvedAt이 null이다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            every { store.findByUsername("newuser") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            every { departmentTreeService.resolveUserAssignment("dept-dev", null) } returns
                (com.ohmyclipping.model.Department(id = "dept-dev", name = "개발팀", nameNormalized = "개발팀") to null)
            val service = AdminAuthService(store, defaultProps(allowUserSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val result = service.registerUser(
                username = "newuser",
                displayName = null,
                departmentId = "dept-dev",
                rawPassword = "StrongPass123!"
            )

            result.approvedAt.shouldBeNull()
            result.approvalStatus shouldBe AccountApprovalStatus.PENDING
        }

        @Test
        fun `username이 공백이면 SIGNUP_INVALID_INPUT 예외를 발생시킨다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            val service = AdminAuthService(store, defaultProps(allowUserSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<SignupException> {
                service.registerUser(
                    username = "   ",
                    displayName = null,
                    departmentId = "dept-dev",
                    rawPassword = "StrongPass123!"
                )
            }.errorCode shouldBe ErrorCode.SIGNUP_INVALID_INPUT
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UsernameValidation
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class UsernameValidation {

        @Test
        fun `3자 미만 사용자명은 거부한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            val service = AdminAuthService(store, defaultProps(allowSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<SignupException> {
                service.registerAdmin(username = "ab", displayName = null, rawPassword = "StrongPass123!")
            }.errorCode.code shouldBe "invalid_username"
        }

        @Test
        fun `100자 초과 사용자명은 거부한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            val service = AdminAuthService(store, defaultProps(allowSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<SignupException> {
                service.registerAdmin(
                    username = "a".repeat(101),
                    displayName = null,
                    rawPassword = "StrongPass123!"
                )
            }.errorCode.code shouldBe "invalid_username"
        }

        @Test
        fun `대문자 포함 사용자명은 소문자로 정규화 후 검증한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            every { store.findByUsername("validuser") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            val service = AdminAuthService(store, defaultProps(allowSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val created = service.registerAdmin(
                username = "ValidUser",
                displayName = null,
                rawPassword = "StrongPass123!"
            )

            created.username shouldBe "validuser"
        }

        @Test
        fun `이메일 형식 사용자명은 허용한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            every { store.findByUsername("user@example.com") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            val service = AdminAuthService(store, defaultProps(allowSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val created = service.registerAdmin(username = "user@example.com", displayName = null, rawPassword = "StrongPass123!")
            created.username shouldBe "user@example.com"
        }

        @Test
        fun `느낌표(!)가 포함된 사용자명은 거부한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            val service = AdminAuthService(store, defaultProps(allowSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<SignupException> {
                service.registerAdmin(username = "user!name", displayName = null, rawPassword = "StrongPass123!")
            }.errorCode.code shouldBe "invalid_username"
        }

        @Test
        fun `점, 밑줄, 하이픈은 허용한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            every { store.findByUsername("user.name_test-1") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            val service = AdminAuthService(store, defaultProps(allowSignup = true), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val created = service.registerAdmin(
                username = "user.name_test-1",
                displayName = null,
                rawPassword = "StrongPass123!"
            )

            created.username shouldBe "user.name_test-1"
        }

        @Test
        fun `isUsernameAvailable은 형식이 맞고 미사용이면 true를 반환한다`() {
            val store = mockk<AdminUserStore>()
            every { store.findByUsername("newuser") } returns null
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            service.isUsernameAvailable("NewUser") shouldBe true
        }

        @Test
        fun `isUsernameAvailable은 이미 존재하면 false를 반환한다`() {
            val store = mockk<AdminUserStore>()
            every { store.findByUsername("existing") } returns AdminUser(
                id = "u1", username = "existing", passwordHash = "h"
            )
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            service.isUsernameAvailable("existing") shouldBe false
        }

        @Test
        fun `isUsernameAvailable은 형식이 틀리면 false를 반환한다`() {
            val store = mockk<AdminUserStore>()
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            service.isUsernameAvailable("ab") shouldBe false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RegisterUserRoles
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class RegisterUserRoles {

        @Test
        fun `USER 등록 시 department가 없으면 거부한다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<SignupException> {
                service.registerUser(
                    username = "testuser",
                    displayName = "테스트",
                    departmentId = null,
                    rawPassword = "StrongPass123!"
                )
            }.errorCode.code shouldBe "invalid_input"
        }

        @Test
        fun `ADMIN 등록 시 department는 필수가 아니다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            every { store.findByUsername("newadmin") } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val created = service.registerAdmin(
                username = "newadmin",
                displayName = null,
                rawPassword = "StrongPass123!"
            )

            created.role shouldBe AccountRole.ADMIN
            created.department shouldBe null
            created.approvalStatus shouldBe AccountApprovalStatus.APPROVED
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PasswordComplexity
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class PasswordComplexity {

        private fun serviceWithStore(): Pair<AdminAuthService, AdminUserStore> {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 0
            every { store.findByUsername(any()) } returns null
            val savedSlot = slot<AdminUser>()
            every { store.save(capture(savedSlot)) } answers { savedSlot.captured }
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)
            return service to store
        }

        @Test
        fun `영문이 없는 비밀번호는 거부한다`() {
            val (service, _) = serviceWithStore()

            val ex = shouldThrow<InvalidInputException> {
                service.registerAdmin(
                    username = "testadmin",
                    displayName = null,
                    rawPassword = "1234567890"
                )
            }
            ex.errorCode shouldBe ErrorCode.SIGNUP_INVALID_PASSWORD
            ex.message shouldContain "letter"
        }

        @Test
        fun `숫자가 없는 비밀번호는 거부한다`() {
            val (service, _) = serviceWithStore()

            val ex = shouldThrow<InvalidInputException> {
                service.registerAdmin(
                    username = "testadmin",
                    displayName = null,
                    rawPassword = "NoDigitsHere"
                )
            }
            ex.errorCode shouldBe ErrorCode.SIGNUP_INVALID_PASSWORD
            ex.message shouldContain "digit"
        }

        @Test
        fun `특수문자나 대문자가 없어도 영문과 숫자만 있으면 허용한다`() {
            val (service, _) = serviceWithStore()

            val created = service.registerAdmin(
                username = "simplepw",
                displayName = null,
                rawPassword = "password12"
            )

            created.username shouldBe "simplepw"
        }

        @Test
        fun `모든 복잡도 조건을 충족하는 비밀번호는 허용한다`() {
            val (service, _) = serviceWithStore()

            val created = service.registerAdmin(
                username = "testadmin",
                displayName = null,
                rawPassword = "StrongPass123!"
            )

            created.username shouldBe "testadmin"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 탈퇴 유저 재가입
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    inner class `탈퇴 유저 재가입` {

        @Test
        fun `탈퇴 계정(isActive=false, REJECTED)은 재활성화되어 PENDING 상태로 복구된다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            // 탈퇴된 기존 계정
            val withdrawn = AdminUser(
                id = "u-withdrawn",
                username = "returned.user",
                passwordHash = "old-hash",
                role = AccountRole.USER,
                isActive = false,
                approvalStatus = AccountApprovalStatus.REJECTED,
                department = "이전부서",
                displayName = "이전이름",
                approvalNote = "탈퇴 처리됨",
                approvedByUserId = "admin-1",
                mustChangePassword = true
            )
            every { store.findByUsername("returned.user") } returns withdrawn
            val updateSlot = slot<AdminUser>()
            every { store.update(capture(updateSlot)) } answers { updateSlot.captured }
            // FK 해석 — legacy 이름 캐시가 "신규부서" 로 갱신되는지 함께 검증한다.
            every { departmentTreeService.resolveUserAssignment("dept-new", null) } returns
                (com.ohmyclipping.model.Department(id = "dept-new", name = "신규부서", nameNormalized = "신규부서") to null)

            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            val result = service.registerUser(
                username = "returned.user",
                displayName = "복귀자",
                departmentId = "dept-new",
                rawPassword = "NewStrong1!",
                slackDmChannelId = "D999"
            )

            // update()가 호출되고 save()는 호출되지 않아야 한다
            verify(exactly = 1) { store.update(any()) }
            verify(exactly = 0) { store.save(any()) }

            // 재활성화된 필드 검증
            result.isActive shouldBe true
            result.approvalStatus shouldBe AccountApprovalStatus.PENDING
            result.department shouldBe "신규부서"
            result.displayName shouldBe "복귀자"
            result.approvalNote.shouldBeNull()
            result.approvedByUserId.shouldBeNull()
            result.approvedAt.shouldBeNull()
            result.mustChangePassword shouldBe false
            result.slackDmChannelId shouldBe "D999"
            passwordEncoder.matches("NewStrong1!", result.passwordHash) shouldBe true

            // 감사 로그가 기록되어야 한다
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = "u-withdrawn",
                    actorName = "returned.user",
                    action = "RE_REGISTER",
                    targetType = "USER",
                    targetId = "u-withdrawn",
                    targetName = "returned.user",
                    detail = "탈퇴 계정 재활성화"
                )
            }
        }

        @Test
        fun `활성 계정은 기존대로 SIGNUP_USERNAME_EXISTS 예외를 발생시킨다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            // 활성 상태의 기존 계정
            every { store.findByUsername("active.user") } returns AdminUser(
                id = "u-active",
                username = "active.user",
                passwordHash = "hash",
                role = AccountRole.USER,
                isActive = true,
                approvalStatus = AccountApprovalStatus.APPROVED,
                department = "개발팀"
            )

            every { departmentTreeService.resolveUserAssignment("dept-dev", null) } returns
                (com.ohmyclipping.model.Department(id = "dept-dev", name = "개발팀", nameNormalized = "개발팀") to null)
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<ConflictException> {
                service.registerUser(
                    username = "active.user",
                    displayName = null,
                    departmentId = "dept-dev",
                    rawPassword = "StrongPass123!"
                )
            }.errorCode shouldBe ErrorCode.SIGNUP_USERNAME_EXISTS
        }

        @Test
        fun `비활성이지만 PENDING 상태인 계정은 재활성화 대상이 아니다`() {
            val store = mockk<AdminUserStore>()
            every { store.countByRole(AccountRole.ADMIN) } returns 1
            // 비활성이지만 PENDING (관리자가 비활성화한 경우 등)
            every { store.findByUsername("pending.user") } returns AdminUser(
                id = "u-pending",
                username = "pending.user",
                passwordHash = "hash",
                role = AccountRole.USER,
                isActive = false,
                approvalStatus = AccountApprovalStatus.PENDING,
                department = "개발팀"
            )

            every { departmentTreeService.resolveUserAssignment("dept-dev", null) } returns
                (com.ohmyclipping.model.Department(id = "dept-dev", name = "개발팀", nameNormalized = "개발팀") to null)
            val service = AdminAuthService(store, defaultProps(), passwordEncoder, auditLogStore, operationsNotificationService, departmentTreeService)

            shouldThrow<ConflictException> {
                service.registerUser(
                    username = "pending.user",
                    displayName = null,
                    departmentId = "dept-dev",
                    rawPassword = "StrongPass123!"
                )
            }.errorCode shouldBe ErrorCode.SIGNUP_USERNAME_EXISTS
        }
    }
}
