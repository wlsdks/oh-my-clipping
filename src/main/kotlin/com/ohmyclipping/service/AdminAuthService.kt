package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsRequestNotificationEvent

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.config.SecurityProperties
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.error.ErrorCode
import com.ohmyclipping.error.SignupException
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.dto.SignupAvailability
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.regex.Pattern

@Service
class AdminAuthService(
    private val adminUserStore: AdminUserStore,
    private val securityProperties: SecurityProperties,
    private val passwordEncoder: PasswordEncoder,
    private val auditLogStore: AuditLogStore,
    private val operationsNotificationService: OperationsNotificationService,
    private val departmentTreeService: DepartmentTreeService
) {
    fun signupAvailability(role: AccountRole): SignupAvailability =
        when (role) {
            AccountRole.ADMIN -> adminSignupAvailability()
            AccountRole.USER -> userSignupAvailability()
        }

    fun registerAdmin(username: String, displayName: String?, rawPassword: String): AdminUser {
        return registerByRole(
            role = AccountRole.ADMIN,
            username = username,
            displayName = displayName,
            departmentId = null,
            teamId = null,
            rawPassword = rawPassword
        )
    }

    /**
     * 사용자 계정을 등록합니다.
     * 등록 직후에는 승인 대기 상태(PENDING)로 저장되며 운영자 승인 전에는 로그인할 수 없습니다.
     *
     * V129 이후 [departmentId] 는 USER 가입에 필수이며, [teamId] 는 선택.
     * FK 해석 후 department/team 이름을 legacy 문자열 컬럼에도 캐시한다.
     */
    fun registerUser(
        username: String,
        displayName: String?,
        departmentId: String?,
        teamId: String? = null,
        rawPassword: String,
        slackDmChannelId: String? = null
    ): AdminUser {
        return registerByRole(
            role = AccountRole.USER,
            username = username,
            displayName = displayName,
            departmentId = departmentId,
            teamId = teamId,
            rawPassword = rawPassword,
            slackDmChannelId = slackDmChannelId
        )
    }

    @Transactional
    private fun registerByRole(
        role: AccountRole,
        username: String,
        displayName: String?,
        departmentId: String?,
        teamId: String?,
        rawPassword: String,
        slackDmChannelId: String? = null
    ): AdminUser {
        val availability = signupAvailability(role)
        if (!availability.allowed) throw SignupException("Signup is disabled", ErrorCode.SIGNUP_DISABLED)

        val normalizedUsername = username.trim().lowercase()
        if (normalizedUsername.isBlank()) {
            throw SignupException("Signup input is invalid", ErrorCode.SIGNUP_INVALID_INPUT)
        }
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw SignupException("Username must be 3-20 chars (letters, numbers, . _ -)", ErrorCode.SIGNUP_INVALID_USERNAME)
        }
        // 비밀번호 복잡도 검증: 최소 길이 + 영문 + 숫자
        validatePasswordComplexity(rawPassword)
        // V129: USER 가입은 departmentId 필수. ADMIN 은 FK 없이도 허용 (부서 없는 글로벌 관리자).
        if (role == AccountRole.USER && departmentId.isNullOrBlank()) {
            throw SignupException("department is required", ErrorCode.SIGNUP_INVALID_INPUT)
        }
        // FK 해석 — 팀 소속이 부서와 일치하는지 여기서 검증해 잘못된 페어를 저장 전에 거부한다.
        val (resolvedDepartment, resolvedTeam) = departmentTreeService.resolveUserAssignment(departmentId, teamId)
        val existing = adminUserStore.findByUsername(normalizedUsername)
        if (existing != null) {
            // 탈퇴 계정(비활성 + REJECTED)이면 재활성화한다.
            if (!existing.isActive && existing.approvalStatus == AccountApprovalStatus.REJECTED) {
                return reactivateAccount(
                    existing = existing,
                    displayName = displayName,
                    rawPassword = rawPassword,
                    slackDmChannelId = slackDmChannelId,
                    department = resolvedDepartment,
                    team = resolvedTeam
                )
            }
            throw ConflictException(
                message = "Username already exists",
                errorCode = ErrorCode.SIGNUP_USERNAME_EXISTS
            )
        }

        val saved = adminUserStore.save(
            AdminUser(
                id = "",
                username = normalizedUsername,
                passwordHash = passwordEncoder.encode(rawPassword),
                role = role,
                displayName = displayName?.trim()?.ifBlank { null },
                // V129: FK 저장 + legacy 이름 캐시 동기화. AdminAuthService.kt:177 Slack 통지가
                // 계속 동작하도록 department / team 문자열 컬럼에도 JOIN 된 이름을 저장한다.
                departmentId = resolvedDepartment?.id,
                teamId = resolvedTeam?.id,
                department = resolvedDepartment?.name,
                team = resolvedTeam?.name,
                slackDmChannelId = slackDmChannelId?.trim()?.ifBlank { null },
                approvalStatus = if (role == AccountRole.USER) AccountApprovalStatus.PENDING else AccountApprovalStatus.APPROVED,
                approvedAt = if (role == AccountRole.ADMIN) Instant.now() else null
            )
        )
        // 가입 요청 알림을 운영 요청 채널에 전송한다.
        notifySignup(saved)
        return saved
    }

    /** 사용자명으로 계정을 조회한다. 존재하지 않으면 null을 반환한다. */
    fun findByUsername(username: String): AdminUser? =
        adminUserStore.findByUsername(username)

    fun updateLastLogin(username: String) {
        adminUserStore.updateLastLoginAt(username)
    }

    /** 탈퇴(비활성+REJECTED) 계정을 재활성화하여 PENDING 상태로 복구한다. */
    private fun reactivateAccount(
        existing: AdminUser,
        displayName: String?,
        rawPassword: String,
        slackDmChannelId: String?,
        department: com.ohmyclipping.model.Department?,
        team: com.ohmyclipping.model.Team?
    ): AdminUser {
        val updated = adminUserStore.update(
            existing.copy(
                passwordHash = passwordEncoder.encode(rawPassword),
                isActive = true,
                approvalStatus = AccountApprovalStatus.PENDING,
                displayName = displayName?.trim()?.ifBlank { null },
                // FK 및 legacy 이름 캐시 동기화.
                departmentId = department?.id,
                teamId = team?.id,
                department = department?.name,
                team = team?.name,
                approvalNote = null,
                approvedByUserId = null,
                approvedAt = null,
                mustChangePassword = false,
                slackDmChannelId = slackDmChannelId?.trim()?.ifBlank { null }
            )
        )
        // 감사 로그에 재가입 이력을 기록한다.
        auditLogStore.log(
            actorId = updated.id,
            actorName = updated.username,
            action = "RE_REGISTER",
            targetType = "USER",
            targetId = updated.id,
            targetName = updated.username,
            detail = "탈퇴 계정 재활성화"
        )
        // 재가입 알림을 운영 요청 채널에 전송한다.
        notifySignup(updated)
        return updated
    }

    /** 가입 요청 알림을 운영 요청 채널에 전송한다. 실패해도 가입 흐름에 영향을 주지 않는다. */
    private fun notifySignup(user: AdminUser) {
        try {
            operationsNotificationService.sendOpsRequest(
                OpsRequestNotificationEvent.USER_SIGNUP_REQUESTED,
                ":wave: 새 가입 요청 — ${user.displayName ?: user.username}" +
                    " (${user.department ?: "부서 미입력"}) / ${user.username}"
            )
        } catch (_: Exception) {
            // 알림 실패는 가입 흐름을 중단하지 않는다.
        }
    }

    private fun adminSignupAvailability(): SignupAvailability {
        val adminCount = adminUserStore.countByRole(AccountRole.ADMIN)
        return when {
            adminCount == 0 && securityProperties.allowBootstrapSignup ->
                SignupAvailability(allowed = true, reason = "first_admin_bootstrap")
            securityProperties.allowSignup ->
                SignupAvailability(allowed = true, reason = "admin_signup_enabled")
            else ->
                SignupAvailability(allowed = false, reason = "admin_signup_disabled")
        }
    }

    private fun userSignupAvailability(): SignupAvailability =
        if (securityProperties.allowUserSignup) {
            SignupAvailability(allowed = true, reason = "user_signup_enabled")
        } else {
            SignupAvailability(allowed = false, reason = "user_signup_disabled")
        }

    /**
     * 아이디 사용 가능 여부를 확인합니다.
     * 형식 불일치 또는 이미 존재하는 아이디이면 false를 반환합니다.
     */
    fun isUsernameAvailable(username: String): Boolean {
        val normalized = username.trim().lowercase()
        if (!USERNAME_PATTERN.matcher(normalized).matches()) return false
        return adminUserStore.findByUsername(normalized) == null
    }

    /**
     * 비밀번호 복잡도를 검증한다.
     *
     * 요구 조건:
     * - 최소 길이 (SecurityProperties.minPasswordLength, 기본 8)
     * - 영문 1개 이상 (대/소문자 무관)
     * - 숫자 1개 이상
     *
     * @throws SignupException 조건 미충족 시
     */
    private fun validatePasswordComplexity(rawPassword: String) {
        if (rawPassword.length < securityProperties.minPasswordLength) {
            throw SignupException(
                "Password must be at least ${securityProperties.minPasswordLength} characters",
                ErrorCode.SIGNUP_INVALID_PASSWORD
            )
        }
        // 영문 포함 여부 확인 (대/소문자 무관)
        ensureValid(rawPassword.any { it.isLetter() }, ErrorCode.SIGNUP_INVALID_PASSWORD) {
            "Password must contain at least one letter"
        }
        // 숫자 포함 여부 확인
        ensureValid(rawPassword.any { it.isDigit() }, ErrorCode.SIGNUP_INVALID_PASSWORD) {
            "Password must contain at least one digit"
        }
    }

    companion object {
        private val USERNAME_PATTERN = Pattern.compile("^[a-z0-9._%@+-]{3,100}$")
    }
}
