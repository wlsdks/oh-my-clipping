package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsRequestNotificationEvent

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.service.dto.BulkActionFailure
import com.ohmyclipping.service.dto.BulkActionResponse
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.UserClippingRequestStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.SecureRandom
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * 사용자 계정 승인/반려 흐름을 관리하는 서비스.
 */
@Service
class UserAccountApprovalService(
    private val adminUserStore: AdminUserStore,
    private val userClippingRequestStore: UserClippingRequestStore,
    private val auditLogStore: AuditLogStore,
    private val passwordEncoder: PasswordEncoder,
    private val runtimeSettingService: RuntimeSettingService,
    private val slackMessageSender: SlackMessageSender,
    private val operationsNotificationService: OperationsNotificationService,
    private val categoryStore: CategoryStore,
    private val auditActorResolver: AuditActorResolver,
    private val departmentTreeService: DepartmentTreeService
) {

    /**
     * USER 계정을 승인 상태별로 조회합니다.
     *
     * @param status 승인 상태 필터 (null이면 전체)
     * @param personaId 페르소나 필터 — 지정 시, 해당 페르소나에 연결된 활성 카테고리를
     *                  APPROVED 구독으로 가진 사용자만 반환한다. null이면 필터링하지 않는다.
     */
    fun listUserAccounts(
        status: AccountApprovalStatus?,
        personaId: String? = null,
        limit: Int? = null
    ): List<AdminUser> {
        val normalizedPersonaId = personaId?.trim()?.takeIf { it.isNotBlank() }
        val accounts = if (normalizedPersonaId == null && limit != null) {
            adminUserStore.listByRole(AccountRole.USER, status, limit)
        } else {
            adminUserStore.listByRole(AccountRole.USER, status)
        }
        // 페르소나 필터가 없으면 기존 동작을 그대로 유지한다.
        if (normalizedPersonaId == null) return accounts

        // 페르소나에 연결된 활성 카테고리 ID 집합을 구한다.
        val categoryIds = categoryStore.findActiveByPersonaId(normalizedPersonaId).map { it.id }.toSet()
        if (categoryIds.isEmpty()) return emptyList()

        // 승인된 구독 중 해당 카테고리를 구독한 사용자 ID만 DB에서 조회한다.
        val matchingUserIds = userClippingRequestStore.findApprovedRequesterIdsByCategoryIds(categoryIds)
        if (matchingUserIds.isEmpty()) return emptyList()

        val filtered = accounts.filter { it.id in matchingUserIds }
        return limit?.let { filtered.take(it.coerceAtLeast(1)) } ?: filtered
    }

    /**
     * 사용자 계정을 승인 처리하고 로그인 가능 상태로 전환합니다.
     */
    @Transactional
    fun approveUserAccount(userId: String, reviewerUsername: String, reviewNote: String?): AdminUser {
        // Bearer 토큰 경로(admin-api)도 승인 처리 가능하도록 리뷰어를 보정한다.
        val reviewer = requireReviewerByUsername(reviewerUsername)
        ensureValid(reviewer.role == AccountRole.ADMIN) { "관리자만 계정을 승인할 수 있습니다." }

        // 승인 대상은 USER 계정만 허용한다.
        val user = requireUserById(userId)
        val now = Instant.now()
        val updated = adminUserStore.update(
            user.copy(
                isActive = true,
                approvalStatus = AccountApprovalStatus.APPROVED,
                approvalNote = reviewNote?.trim()?.ifBlank { null },
                approvedByUserId = reviewer.id,
                approvedAt = now
            )
        )
        // 커밋 이후에 외부 알림을 보내 DB 롤백과 알림 불일치를 방지한다.
        afterCommit {
            operationsNotificationService.sendOpsRequest(
                OpsRequestNotificationEvent.USER_ACCOUNT_APPROVED,
                ":white_check_mark: 가입 승인 — ${updated.username}"
            )
            trySendAccountDm(updated, ":tada: 가입이 승인되었습니다!\n\nClipping 서비스에 로그인할 수 있습니다.")
        }
        return updated
    }

    /**
     * 사용자 계정을 반려 처리하고 로그인 불가 상태로 전환합니다.
     */
    @Transactional
    fun rejectUserAccount(userId: String, reviewerUsername: String, reviewNote: String?): AdminUser {
        // Bearer 토큰 경로(admin-api)도 반려 처리 가능하도록 리뷰어를 보정한다.
        val reviewer = requireReviewerByUsername(reviewerUsername)
        ensureValid(reviewer.role == AccountRole.ADMIN) { "관리자만 계정을 반려할 수 있습니다." }

        // 반려 사유는 운영 추적을 위해 필수로 입력한다.
        val normalizedNote = reviewNote?.trim()?.ifBlank { null }
        ensureValid(!normalizedNote.isNullOrBlank()) { "반려 사유를 입력해 주세요." }

        // 반려 대상은 USER 계정만 허용한다.
        val user = requireUserById(userId)
        val now = Instant.now()
        val updated = adminUserStore.update(
            user.copy(
                isActive = false,
                approvalStatus = AccountApprovalStatus.REJECTED,
                approvalNote = normalizedNote,
                approvedByUserId = reviewer.id,
                approvedAt = now
            )
        )
        // 커밋 이후에 외부 알림을 보내 DB 롤백과 알림 불일치를 방지한다.
        val reason = reviewNote?.trim()?.ifBlank { null } ?: "사유가 명시되지 않았습니다"
        afterCommit {
            operationsNotificationService.sendOpsRequest(
                OpsRequestNotificationEvent.USER_ACCOUNT_REJECTED,
                ":x: 가입 반려 — ${updated.username}"
            )
            trySendAccountDm(updated, "가입 신청이 반려되었습니다.\n\n사유: $reason\n문의사항이 있으시면 관리자에게 Slack으로 연락해 주세요.")
        }
        return updated
    }

    /**
     * 사용자 계정을 탈퇴 처리한다.
     * 계정을 비활성화하고, 해당 사용자의 APPROVED 구독을 WITHDRAWN으로 전환한다.
     * Slack 정리 완료 확인 후 관리자가 호출한다.
     *
     * @param userId 탈퇴 대상 사용자 ID
     * @param reviewerUsername 처리 관리자 사용자명
     * @param reviewNote 탈퇴 사유/메모 (선택)
     * @return 업데이트된 사용자 정보
     */
    @Transactional
    fun withdrawUserAccount(userId: String, reviewerUsername: String, reviewNote: String?): AdminUser {
        // 관리자 권한을 검증한다.
        val reviewer = requireReviewerByUsername(reviewerUsername)
        ensureValid(reviewer.role == AccountRole.ADMIN) { "관리자만 계정 탈퇴를 처리할 수 있습니다." }

        // 탈퇴 대상은 USER 계정만 허용한다.
        val user = requireUserById(userId)

        // 해당 사용자의 APPROVED 구독을 WITHDRAWN 상태로 일괄 전환한다.
        val approvedRequestIds = userClippingRequestStore.listByRequesterUserId(userId)
            .filter { it.status == UserClippingRequestStatus.APPROVED }
            .map { it.id }
        if (approvedRequestIds.isNotEmpty()) {
            userClippingRequestStore.updateStatusBulk(
                ids = approvedRequestIds,
                status = UserClippingRequestStatus.WITHDRAWN,
                reviewNote = "탈퇴 처리로 인한 자동 해제",
                reviewedByUserId = reviewer.id
            )
        }

        // 계정을 비활성화하고 상태를 REJECTED로 전환한다.
        // Slack 멤버 ID와 DM 채널 ID를 제거해 탈퇴 후 알림/발송이 차단되도록 한다.
        val normalizedNote = reviewNote?.trim()?.ifBlank { null } ?: "탈퇴 처리"
        val now = Instant.now()
        val updated = adminUserStore.update(
            user.copy(
                isActive = false,
                approvalStatus = AccountApprovalStatus.REJECTED,
                approvalNote = normalizedNote,
                approvedByUserId = reviewer.id,
                approvedAt = now,
                slackMemberId = null,
                slackDmChannelId = null
            )
        )

        // 감사 로그에 탈퇴 처리 기록을 남긴다.
        auditLogStore.log(
            actorId = reviewer.id,
            actorName = reviewerUsername,
            action = "DEACTIVATE",
            targetType = "USER",
            targetId = userId,
            targetName = updated.username,
            detail = reviewNote
        )

        return updated
    }

    /**
     * 사용자 본인이 직접 계정을 탈퇴한다 (셀프 서비스).
     *
     * 비밀번호를 확인한 후, 기존 관리자 탈퇴 로직과 동일하게
     * 구독 해제 → 계정 비활성화 → 감사 로그 기록을 수행한다.
     *
     * @param username 탈퇴 요청 사용자명 (인증 principal에서 추출)
     * @param rawPassword 본인 확인용 비밀번호
     * @return 업데이트된 사용자 정보
     */
    @Transactional
    fun selfWithdraw(username: String, rawPassword: String): AdminUser {
        val user = adminUserStore.findByUsername(username)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다")
        ensureValid(user.role == AccountRole.USER) { "일반 사용자 계정만 탈퇴할 수 있습니다." }
        ensureValid(user.isActive) { "이미 비활성화된 계정입니다." }

        // 비밀번호 확인으로 본인 여부를 검증한다.
        ensureValid(passwordMatches(user.passwordHash, rawPassword)) {
            "비밀번호가 올바르지 않습니다."
        }

        // APPROVED 구독을 WITHDRAWN 상태로 일괄 전환한다.
        val approvedRequestIds = userClippingRequestStore.listByRequesterUserId(user.id)
            .filter { it.status == UserClippingRequestStatus.APPROVED }
            .map { it.id }
        if (approvedRequestIds.isNotEmpty()) {
            userClippingRequestStore.updateStatusBulk(
                ids = approvedRequestIds,
                status = UserClippingRequestStatus.WITHDRAWN,
                reviewNote = "본인 탈퇴 요청으로 인한 자동 해제",
                reviewedByUserId = user.id
            )
        }

        // 계정을 비활성화한다.
        val now = Instant.now()
        val updated = adminUserStore.update(
            user.copy(
                isActive = false,
                approvalStatus = AccountApprovalStatus.REJECTED,
                approvalNote = "본인 탈퇴 요청",
                approvedByUserId = user.id,
                approvedAt = now,
                slackMemberId = null,
                slackDmChannelId = null
            )
        )

        auditLogStore.log(
            actorId = user.id,
            actorName = username,
            action = "SELF_WITHDRAW",
            targetType = "USER",
            targetId = user.id,
            targetName = updated.username,
            detail = "사용자 본인 탈퇴 요청"
        )

        return updated
    }

    /**
     * 사용자 본인의 Slack 멤버 ID를 업데이트한다.
     * DM 구독 시 이 ID로 Slack DM을 발송한다.
     *
     * @param username 인증된 사용자명 (principal)
     * @param slackMemberId Slack 멤버 ID (U로 시작, 8~15자)
     */
    fun updateSlackMemberId(username: String, slackMemberId: String) {
        // 입력값을 정규화하고 빈 값 여부를 검증한다.
        val trimmed = slackMemberId.trim().uppercase()
        ensureValid(trimmed.isNotBlank()) { "Slack 멤버 ID를 입력해 주세요." }
        // Slack 멤버 ID 형식을 검증한다 (U + 영숫자 7~14자).
        ensureValid(trimmed.matches(Regex("^U[A-Z0-9]{7,14}$"))) {
            "U로 시작하는 멤버 ID를 입력해 주세요 (예: U01AB2CD3EF)."
        }
        // 사용자를 조회한다.
        val user = adminUserStore.findByUsername(username)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다")
        val oldMemberId = user.slackMemberId

        // 멤버 ID로 DM 채널을 자동 획득한다.
        // conversations.open(im:write 필요) 시도 → 실패 시 멤버 ID를 DM 채널로 직접 사용
        // (chat.postMessage는 U... 멤버 ID를 채널로 받으면 자동 DM 전송)
        val dmChannelId = runCatching {
            val runtime = runtimeSettingService.current()
            val botToken = runtime.slackBotToken.takeIf { it.isNotBlank() }
            slackMessageSender.openDmChannel(botToken, trimmed)
        }.getOrElse {
            // im:write 스코프 없으면 멤버 ID 자체를 DM 채널로 사용
            trimmed
        }

        // 멤버 ID와 DM 채널 ID를 분리 저장한다.
        adminUserStore.update(user.copy(
            slackMemberId = trimmed,
            slackDmChannelId = dmChannelId
        ))
        // 감사 로그에 변경 이력을 기록한다.
        auditLogStore.log(
            actorId = user.id,
            actorName = username,
            action = "SLACK_MEMBER_ID_UPDATED",
            targetType = "USER",
            targetId = user.id,
            targetName = user.username,
            detail = "slackMemberId: ${oldMemberId ?: "null"} → $trimmed, dmChannelId: $dmChannelId"
        )
    }


    /**
     * V129: 사용자 본인이 자신의 부서/팀 FK 를 수정한다.
     *
     * 규칙:
     * - null 필드는 변경하지 않고 기존 값을 유지한다. (departmentId/teamId 모두 null → 변경 없음)
     * - 빈 문자열 departmentId 는 부서 초기화(null) 로 해석하며, 이 경우 teamId 도 반드시 빈 문자열/null 이어야 한다.
     * - teamId 가 지정되면 해당 팀의 departmentId 가 요청한 departmentId 와 일치해야 한다 (ConflictException 로 거부).
     * - 저장 후 legacy `admin_users.department` / `team` 이름 캐시도 함께 갱신한다 (Slack 통지 호환성).
     *
     * @return 업데이트된 사용자 엔티티
     * @throws NotFoundException 사용자/부서/팀이 존재하지 않을 때
     * @throws ConflictException 팀의 departmentId 가 선택한 부서와 다를 때
     */
    fun updateSelfProfile(
        username: String,
        departmentId: String?,
        teamId: String?
    ): AdminUser {
        // 사용자를 조회한다. 없으면 예외.
        val user = adminUserStore.findByUsername(username)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다")

        // null → 기존 유지, 빈 문자열 → 초기화로 해석한다. team 은 department 에 종속된 3-state 입력.
        val nextDepartmentId = when {
            departmentId == null -> user.departmentId
            departmentId.isBlank() -> null
            else -> departmentId
        }
        val nextTeamId = when {
            // 부서가 null 로 바뀌면 팀도 강제 null.
            nextDepartmentId == null -> null
            teamId == null -> user.teamId
            teamId.isBlank() -> null
            else -> teamId
        }

        // 변경 사항이 없으면 store 호출 없이 현재 값을 반환한다.
        if (nextDepartmentId == user.departmentId && nextTeamId == user.teamId) {
            return user
        }

        // FK 일관성 검증 (department 존재/활성 여부 + team.departmentId == departmentId).
        val (resolvedDepartment, resolvedTeam) = departmentTreeService.resolveUserAssignment(
            departmentId = nextDepartmentId,
            teamId = nextTeamId
        )

        val updated = adminUserStore.update(
            user.copy(
                departmentId = resolvedDepartment?.id,
                teamId = resolvedTeam?.id,
                // Legacy 이름 캐시 동기화 — Slack 통지(AdminAuthService.kt:177) 및 분석 쿼리 호환.
                department = resolvedDepartment?.name,
                team = resolvedTeam?.name
            )
        )
        // 감사 로그에 변경 이력을 기록한다.
        auditLogStore.log(
            actorId = updated.id,
            actorName = username,
            action = "PROFILE_UPDATED",
            targetType = "USER",
            targetId = updated.id,
            targetName = updated.username,
            detail = "departmentId: ${user.departmentId ?: "null"} → ${resolvedDepartment?.id ?: "null"}, " +
                "teamId: ${user.teamId ?: "null"} → ${resolvedTeam?.id ?: "null"}"
        )
        return updated
    }

    /**
     * 사용자 본인이 비밀번호를 변경한다. 현재 비밀번호 검증 + 복잡도 검증 후
     * 새 해시로 저장하고 must_change_password 플래그를 false 로 클리어한다.
     *
     * @throws NotFoundException 사용자가 존재하지 않을 때
     * @throws InvalidInputException 현재 비밀번호 불일치 / 복잡도 미달 / 동일 비밀번호
     */
    fun changePassword(username: String, currentPassword: String, newPassword: String) {
        // 현재 사용자 조회 — username 은 로그인 세션 기준이므로 소문자 정규화.
        val user = adminUserStore.findByUsername(username.trim().lowercase())
            ?: throw NotFoundException("사용자를 찾을 수 없습니다")
        // 현재 비밀번호가 맞는지 검증.
        if (!passwordMatches(user.passwordHash, currentPassword)) {
            throw InvalidInputException("현재 비밀번호가 올바르지 않습니다")
        }
        // 새 비밀번호 복잡도 검증 (AGENTS.md §9.4 — 최소 8자 + 영문 1 + 숫자 1).
        validateNewPasswordComplexity(newPassword)
        // 현재와 동일한 비밀번호는 거부 — 사용자가 실수로 그대로 제출하는 것 방지.
        if (passwordMatches(user.passwordHash, newPassword)) {
            throw InvalidInputException("새 비밀번호는 기존 비밀번호와 달라야 합니다")
        }
        // 해시 후 저장 — must_change_password 플래그도 함께 false 로 클리어.
        val hashedPassword = passwordEncoder.encode(newPassword)
        adminUserStore.updatePasswordHashAndFlags(user.id, hashedPassword, mustChangePassword = false)

        // 감사 로그 기록 — actor 는 본인.
        val actor = auditActorResolver.resolve(username)
        auditLogStore.log(
            actorId = actor.id,
            actorName = actor.name,
            action = "PASSWORD_CHANGED_BY_SELF",
            targetType = "USER",
            targetId = user.id,
            targetName = user.username,
            detail = "사용자 본인 비밀번호 변경"
        )
    }

    /**
     * 비밀번호 복잡도 검증 (본인 변경 경로 전용).
     * AdminAuthService.validatePasswordComplexity 와 동일 정책을 InvalidInputException 으로 포장.
     */
    private fun validateNewPasswordComplexity(rawPassword: String) {
        // AGENTS.md §9.4 기준: 최소 8자.
        if (rawPassword.length < 8) {
            throw InvalidInputException("비밀번호는 최소 8자 이상이어야 합니다")
        }
        if (!rawPassword.any { it.isLetter() }) {
            throw InvalidInputException("비밀번호에 영문을 포함해야 합니다")
        }
        if (!rawPassword.any { it.isDigit() }) {
            throw InvalidInputException("비밀번호에 숫자를 포함해야 합니다")
        }
    }

    /**
     * 관리자가 사용자 비밀번호를 임시 비밀번호로 초기화한다.
     * @return 생성된 임시 비밀번호 (평문) — 호출자가 즉시 사용자에게 전달해야 한다.
     */
    fun resetPassword(userId: String, resetByUsername: String? = null): String {
        // 대상 사용자를 조회한다.
        val user = adminUserStore.findById(userId)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다")
        // 임시 비밀번호를 생성하고 해시하여 저장한다.
        val tempPassword = generateTempPassword()
        val hashedPassword = passwordEncoder.encode(tempPassword)
        adminUserStore.updatePasswordHash(userId, hashedPassword)

        // 감사 로그에 비밀번호 초기화 이력을 기록한다.
        val actor = auditActorResolver.resolve(resetByUsername)
        auditLogStore.log(
            actorId = actor.id,
            actorName = actor.name,
            action = "PASSWORD_RESET",
            targetType = "USER",
            targetId = userId,
            targetName = user.username,
            detail = "관리자에 의한 임시 비밀번호 초기화"
        )
        // 비밀번호 초기화 알림은 DB 반영 커밋 이후 운영 요청 채널에 전송한다.
        afterCommit {
            operationsNotificationService.sendOpsRequest(
                OpsRequestNotificationEvent.PASSWORD_RESET_COMPLETED,
                ":key: 비밀번호 초기화 — ${user.username}"
            )
        }

        return tempPassword
    }

    private fun generateTempPassword(): String {
        // 혼동되기 쉬운 문자(O, 0, I, l, 1)를 제외한 안전한 문자 집합에서 12자 생성한다.
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#"
        val secureRandom = SecureRandom()
        return (1..12).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    private fun passwordMatches(hash: String, raw: String): Boolean {
        // 공유 PasswordEncoder 빈을 사용해 매 호출마다 인스턴스 생성 비용을 줄인다.
        return passwordEncoder.matches(raw, hash)
    }

    /**
     * 현재 트랜잭션이 있으면 커밋 이후에 실행하고, 없으면 즉시 실행한다.
     * Slack/운영 알림처럼 DB 상태가 확정된 뒤 나가야 하는 외부 부수효과에 사용한다.
     */
    private fun afterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safelyRunAfterCommitAction(action)
            return
        }
        // Spring 트랜잭션 동기화에 등록해 커밋 성공 이후에만 외부 호출을 실행한다.
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    safelyRunAfterCommitAction(action)
                }
            }
        )
    }

    /**
     * 커밋 이후 알림 실패는 이미 확정된 DB 변경을 되돌리지 않고 로그만 남긴다.
     */
    private fun safelyRunAfterCommitAction(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            log.warn(e) { "계정 처리 후속 알림 실패" }
        }
    }

    /** 특정 승인 상태의 USER 계정 수를 반환한다. */
    fun countByStatus(status: AccountApprovalStatus): Int =
        adminUserStore.countByRoleAndStatus(AccountRole.USER, status)

    /** 특정 시점 이후 승인/반려 처리된 USER 계정 수를 반환한다. */
    fun countProcessedSince(since: Instant): Int =
        adminUserStore.countProcessedSince(AccountRole.USER, since)

    /**
     * 승인된 요청을 유저별로 그룹화하여 구독 건수를 반환한다.
     * SQL GROUP BY로 집계하여 전체 행 로드를 방지한다.
     */
    fun getSubscriptionCountByUser(): Map<String, Int> =
        userClippingRequestStore.countApprovedGroupByRequester()

    /**
     * 승인자 ID 목록을 받아 각 ID에 대한 표시 이름(displayName)을 반환한다.
     * 존재하지 않는 ID는 null 값으로 매핑된다.
     */
    fun getApproverDisplayNames(approverIds: List<String>): Map<String, String?> {
        if (approverIds.isEmpty()) return emptyMap()
        return adminUserStore.findByIds(approverIds)
            .associate { it.id to it.displayName }
    }

    /**
     * 사용자 계정을 일괄 승인 처리한다.
     * 개별 실패가 전체를 롤백하지 않으며, 결과를 succeeded/failed로 분리 반환한다.
     */
    fun bulkApproveUserAccounts(
        ids: List<String>,
        reviewerUsername: String,
        reviewNote: String?
    ): BulkActionResponse {
        val reviewer = requireReviewerByUsername(reviewerUsername)
        ensureValid(reviewer.role == AccountRole.ADMIN) { "관리자만 계정을 승인할 수 있습니다." }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<BulkActionFailure>()

        // ID 목록을 일괄 조회해 N+1 findById 호출을 방지한다.
        val usersById = adminUserStore.findByIds(ids).associateBy { it.id }

        for (id in ids) {
            try {
                // 대상 사용자를 조회하고 상태를 검증한다.
                val user = usersById[id]
                    ?: throw NotFoundException("사용자를 찾을 수 없습니다: $id")
                ensureValid(user.role == AccountRole.USER) { "일반 사용자 계정만 심사할 수 있습니다." }
                ensureValid(user.approvalStatus == AccountApprovalStatus.PENDING) {
                    "이미 ${user.approvalStatus.name} 상태인 계정입니다"
                }
                // 승인 상태로 전환한다.
                adminUserStore.update(
                    user.copy(
                        isActive = true,
                        approvalStatus = AccountApprovalStatus.APPROVED,
                        approvalNote = reviewNote?.trim()?.ifBlank { null },
                        approvedByUserId = reviewer.id,
                        approvedAt = Instant.now()
                    )
                )
                succeeded.add(id)
            } catch (e: NotFoundException) {
                failed.add(BulkActionFailure(id = id, reason = e.message, code = "NOT_FOUND"))
            } catch (e: InvalidInputException) {
                val code = if (e.message.contains("상태")) "ALREADY_PROCESSED" else "INVALID_ROLE"
                failed.add(BulkActionFailure(id = id, reason = e.message, code = code))
            } catch (e: Exception) {
                failed.add(BulkActionFailure(id = id, reason = e.message ?: "알 수 없는 오류", code = "UNKNOWN"))
            }
        }

        return BulkActionResponse(succeeded = succeeded, failed = failed)
    }

    /**
     * 사용자 계정을 일괄 반려 처리한다.
     * 반려 사유(reviewNote)는 필수이며, 개별 실패가 전체를 롤백하지 않는다.
     */
    fun bulkRejectUserAccounts(
        ids: List<String>,
        reviewerUsername: String,
        reviewNote: String?
    ): BulkActionResponse {
        val reviewer = requireReviewerByUsername(reviewerUsername)
        ensureValid(reviewer.role == AccountRole.ADMIN) { "관리자만 계정을 반려할 수 있습니다." }
        // 반려 사유는 운영 추적을 위해 필수로 입력한다.
        val normalizedNote = reviewNote?.trim()?.ifBlank { null }
        ensureValid(!normalizedNote.isNullOrBlank()) { "반려 사유를 입력해 주세요." }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<BulkActionFailure>()

        // ID 목록을 일괄 조회해 N+1 findById 호출을 방지한다.
        val usersById = adminUserStore.findByIds(ids).associateBy { it.id }

        for (id in ids) {
            try {
                // 대상 사용자를 조회하고 상태를 검증한다.
                val user = usersById[id]
                    ?: throw NotFoundException("사용자를 찾을 수 없습니다: $id")
                ensureValid(user.role == AccountRole.USER) { "일반 사용자 계정만 심사할 수 있습니다." }
                ensureValid(user.approvalStatus == AccountApprovalStatus.PENDING) {
                    "이미 ${user.approvalStatus.name} 상태인 계정입니다"
                }
                // 반려 상태로 전환한다.
                adminUserStore.update(
                    user.copy(
                        isActive = false,
                        approvalStatus = AccountApprovalStatus.REJECTED,
                        approvalNote = normalizedNote,
                        approvedByUserId = reviewer.id,
                        approvedAt = Instant.now()
                    )
                )
                succeeded.add(id)
            } catch (e: NotFoundException) {
                failed.add(BulkActionFailure(id = id, reason = e.message, code = "NOT_FOUND"))
            } catch (e: InvalidInputException) {
                val code = if (e.message.contains("상태")) "ALREADY_PROCESSED" else "INVALID_ROLE"
                failed.add(BulkActionFailure(id = id, reason = e.message, code = code))
            } catch (e: Exception) {
                failed.add(BulkActionFailure(id = id, reason = e.message ?: "알 수 없는 오류", code = "UNKNOWN"))
            }
        }

        return BulkActionResponse(succeeded = succeeded, failed = failed)
    }

    /**
     * 리뷰어 계정을 조회한다.
     * form-login 사용자명은 그대로 사용하고, API 토큰 principal(admin-api)은 활성 ADMIN 계정으로 대체한다.
     */
    private fun requireReviewerByUsername(username: String): AdminUser {
        val normalized = username.trim().lowercase()
        // 일반 로그인 사용자명은 우선 직접 조회한다.
        adminUserStore.findByUsername(normalized)?.let { return it }
        // API 토큰 principal은 승인된 ADMIN 중 첫 번째 계정으로 대체한다.
        if (normalized == "admin-api") {
            val fallbackReviewer = adminUserStore
                .listByRole(AccountRole.ADMIN, AccountApprovalStatus.APPROVED)
                .firstOrNull { it.isActive }
            if (fallbackReviewer != null) {
                return fallbackReviewer
            }
            throw NotFoundException("승인된 관리자를 찾을 수 없습니다.")
        }
        throw NotFoundException("사용자를 찾을 수 없습니다: $username")
    }

    /** 계정 관련 Slack DM을 발송한다. 실패 시 로그만 남긴다. */
    private fun trySendAccountDm(user: AdminUser, message: String) {
        val channelId = user.slackDmChannelId
        if (channelId.isNullOrBlank()) return
        try {
            slackMessageSender.sendMessage(channelId = channelId, text = message)
        } catch (e: Exception) {
            log.warn(e) { "계정 DM 발송 실패: userId=${user.id}" }
        }
    }

    private fun requireUserById(userId: String): AdminUser {
        // 존재 여부와 역할(USER) 제약을 동시에 검증한다.
        val user = adminUserStore.findById(userId)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다: $userId")
        ensureValid(user.role == AccountRole.USER) { "일반 사용자 계정만 심사할 수 있습니다." }
        return user
    }
}
