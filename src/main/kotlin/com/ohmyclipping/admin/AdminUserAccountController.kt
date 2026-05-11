package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.BulkReviewUserAccountRequest
import com.ohmyclipping.admin.dto.ResetPasswordResponse
import com.ohmyclipping.admin.dto.ReviewUserAccountRequest
import com.ohmyclipping.admin.dto.UserAccountApprovalResponse
import com.ohmyclipping.admin.dto.UserAccountSummaryResponse
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.service.UserAccountApprovalService
import com.ohmyclipping.service.UserEventService
import com.ohmyclipping.service.dto.BulkActionResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.temporal.ChronoUnit

/**
 * 관리자용 사용자 계정 승인/반려 API.
 */
@RestController
@RequestMapping("/api/admin/user-accounts")
class AdminUserAccountController(
    private val userAccountApprovalService: UserAccountApprovalService,
    private val userEventService: UserEventService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) personaId: String?,
        @RequestParam(defaultValue = "50") size: Int
    ): List<UserAccountApprovalResponse> {
        val safeSize = size.coerceIn(1, 200)
        val normalizedStatus = status?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            try {
                AccountApprovalStatus.valueOf(raw.uppercase())
            } catch (_: IllegalArgumentException) {
                throw InvalidInputException("Unsupported approval status: $raw")
            }
        }
        // 페르소나 필터는 빈 문자열을 null로 정규화해 기존 동작을 유지한다.
        val normalizedPersonaId = personaId?.trim()?.takeIf { it.isNotBlank() }
        val accounts = userAccountApprovalService.listUserAccounts(normalizedStatus, normalizedPersonaId, safeSize)
        // 서비스를 통해 유저별 구독 수를 조회한다.
        val subscriptionCountByUser = userAccountApprovalService.getSubscriptionCountByUser()

        // APPROVED 상태에서만 활동 요약을 조회한다 (PENDING/REJECTED는 로그인 불가이므로 스킵).
        val activitySummaryByUser: Map<String, String?> =
            if (normalizedStatus == AccountApprovalStatus.APPROVED || normalizedStatus == null) {
                val approvedUserIds = accounts
                    .filter { it.approvalStatus == AccountApprovalStatus.APPROVED }
                    .map { it.id }
                userEventService.buildRecentActivitySummaryBatch(approvedUserIds)
            } else {
                emptyMap()
            }

        // 승인자 ID → 표시 이름 매핑을 한번에 조회한다.
        val approverIds = accounts.mapNotNull { it.approvedByUserId }.distinct()
        val usernameByUserId = userAccountApprovalService.getApproverDisplayNames(approverIds)
        return accounts.map {
            it.toResponse(subscriptionCountByUser, activitySummaryByUser, usernameByUserId)
        }
    }

    @GetMapping("/summary")
    fun summary(): UserAccountSummaryResponse {
        val pendingCount = userAccountApprovalService.countByStatus(AccountApprovalStatus.PENDING)
        val rejectedCount = userAccountApprovalService.countByStatus(AccountApprovalStatus.REJECTED)
        val weeklyProcessedCount = userAccountApprovalService.countProcessedSince(
            java.time.Instant.now().minus(7, ChronoUnit.DAYS)
        )
        return UserAccountSummaryResponse(pendingCount, rejectedCount, weeklyProcessedCount)
    }

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: String,
        authentication: Authentication,
        @RequestBody(required = false) request: ReviewUserAccountRequest?
    ): UserAccountApprovalResponse =
        userAccountApprovalService.approveUserAccount(
            userId = id,
            reviewerUsername = authentication.name,
            reviewNote = request?.reviewNote
        ).toResponse()

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: String,
        authentication: Authentication,
        @RequestBody request: ReviewUserAccountRequest
    ): UserAccountApprovalResponse =
        userAccountApprovalService.rejectUserAccount(
            userId = id,
            reviewerUsername = authentication.name,
            reviewNote = request.reviewNote
        ).toResponse()

    @PostMapping("/{id}/withdraw")
    fun withdraw(
        @PathVariable id: String,
        authentication: Authentication,
        @RequestBody(required = false) request: ReviewUserAccountRequest?
    ): UserAccountApprovalResponse =
        userAccountApprovalService.withdrawUserAccount(
            userId = id,
            reviewerUsername = authentication.name,
            reviewNote = request?.reviewNote
        ).toResponse()


    @PostMapping("/{id}/reset-password")
    fun resetPassword(@PathVariable id: String, authentication: Authentication): ResetPasswordResponse {
        val tempPassword = userAccountApprovalService.resetPassword(id, resetByUsername = authentication.name)
        return ResetPasswordResponse(
            tempPassword = tempPassword,
            message = "임시 비밀번호가 생성되었습니다. 사용자에게 전달해 주세요."
        )
    }

    @PostMapping("/bulk-approve")
    fun bulkApprove(
        authentication: Authentication,
        @RequestBody request: BulkReviewUserAccountRequest
    ): BulkActionResponse {
        ensureValid(request.ids.isNotEmpty()) { "처리할 항목을 선택해 주세요." }
        ensureValid(request.ids.size <= 50) { "일괄 처리는 최대 50건까지 가능합니다." }
        return userAccountApprovalService.bulkApproveUserAccounts(
            ids = request.ids,
            reviewerUsername = authentication.name,
            reviewNote = request.reviewNote
        )
    }

    @PostMapping("/bulk-reject")
    fun bulkReject(
        authentication: Authentication,
        @RequestBody request: BulkReviewUserAccountRequest
    ): BulkActionResponse {
        ensureValid(request.ids.isNotEmpty()) { "처리할 항목을 선택해 주세요." }
        ensureValid(request.ids.size <= 50) { "일괄 처리는 최대 50건까지 가능합니다." }
        return userAccountApprovalService.bulkRejectUserAccounts(
            ids = request.ids,
            reviewerUsername = authentication.name,
            reviewNote = request.reviewNote
        )
    }

    private fun AdminUser.toResponse(
        subscriptionCountByUser: Map<String, Int> = emptyMap(),
        activitySummaryByUser: Map<String, String?> = emptyMap(),
        usernameByUserId: Map<String, String?> = emptyMap()
    ) = UserAccountApprovalResponse(
        id = id,
        username = username,
        displayName = displayName,
        department = department,
        team = team,
        isActive = isActive,
        approvalStatus = approvalStatus.name,
        approvalNote = approvalNote,
        approvedByUserId = approvedByUserId,
        approvedAt = approvedAt?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        lastLoginAt = lastLoginAt?.toString(),
        subscriptionCount = subscriptionCountByUser[id] ?: 0,
        recentActivitySummary = activitySummaryByUser[id],
        role = role.name,
        approvedByUsername = approvedByUserId?.let { usernameByUserId[it] ?: "(알 수 없음)" }
    )
}
