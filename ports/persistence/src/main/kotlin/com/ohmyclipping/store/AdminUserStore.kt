package com.ohmyclipping.store

import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AdminUser
import java.time.Instant

interface AdminUserStore {
    fun count(): Int
    fun countByRole(role: AccountRole): Int
    fun findById(id: String): AdminUser?
    fun findByUsername(username: String): AdminUser?
    fun listByRole(role: AccountRole, approvalStatus: AccountApprovalStatus? = null): List<AdminUser>

    /** 역할/승인 상태 기준 사용자 목록을 최신 생성순으로 제한 조회한다. */
    fun listByRole(
        role: AccountRole,
        approvalStatus: AccountApprovalStatus? = null,
        limit: Int
    ): List<AdminUser>

    fun findPendingUsersCreatedBefore(cutoff: Instant): List<AdminUser>

    /** ID 목록으로 사용자를 일괄 조회한다. 존재하지 않는 ID는 결과에서 제외된다. */
    fun findByIds(ids: List<String>): List<AdminUser>

    /** 역할 + 승인 상태 기준으로 사용자 수를 센다. */
    fun countByRoleAndStatus(role: AccountRole, status: AccountApprovalStatus): Int

    /** 부서 FK 를 참조 중인 사용자 수. 부서 하드 삭제 전 가드로 사용. */
    fun countByDepartmentId(departmentId: String): Int

    /** 팀 FK 를 참조 중인 사용자 수. 팀 하드 삭제 전 가드로 사용. */
    fun countByTeamId(teamId: String): Int

    /** 특정 시점 이후 승인 또는 반려 처리된 사용자 수를 센다. */
    fun countProcessedSince(role: AccountRole, since: Instant): Int

    /** Slack 멤버 ID(U...)로 사용자를 조회한다. 피드백/이벤트에서 Slack ID → UUID 변환에 사용. */
    fun findBySlackMemberId(slackMemberId: String): AdminUser?

    fun save(user: AdminUser): AdminUser
    fun update(user: AdminUser): AdminUser
    fun updateLastLoginAt(username: String)

    /** 비활성(탈퇴) 후 cutoff 이전에 승인 해제된 사용자의 PII를 익명화한다. 익명화된 건수를 반환. */
    fun anonymizeDeactivatedBefore(cutoff: Instant): Int

    /** 사용자의 비밀번호 해시를 직접 업데이트한다. 임시 비밀번호 초기화 등에 사용한다. */
    fun updatePasswordHash(userId: String, passwordHash: String)

    /** 비밀번호 해시와 mustChangePassword 플래그를 원자적으로 갱신한다. 계정 복구/비밀번호 리셋에 사용한다. */
    fun updatePasswordHashAndFlags(userId: String, passwordHash: String, mustChangePassword: Boolean)
}
