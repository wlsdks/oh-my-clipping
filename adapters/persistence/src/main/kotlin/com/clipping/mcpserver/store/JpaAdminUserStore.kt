package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.AdminUserEntity
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.repository.AdminUserRepository
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 관리자/사용자 계정 JPA 구현. JdbcAdminUserStore를 대체한다.
 */
@Repository
@Primary
class JpaAdminUserStore(
    private val repository: AdminUserRepository
) : AdminUserStore {

    private companion object {
        /** 관리자 계정 목록 제한 조회 안전 상한. */
        private const val MAX_LIST_BY_ROLE_LIMIT = 500
    }

    override fun count(): Int = repository.count().toInt()

    override fun countByRole(role: AccountRole): Int = repository.countByRole(role.name)

    override fun countByRoleAndStatus(role: AccountRole, status: AccountApprovalStatus): Int =
        repository.countByRoleAndApprovalStatus(role.name, status.name)

    override fun countByDepartmentId(departmentId: String): Int =
        repository.countByDepartmentId(departmentId)

    override fun countByTeamId(teamId: String): Int =
        repository.countByTeamId(teamId)

    override fun countProcessedSince(role: AccountRole, since: Instant): Int =
        repository.countProcessedSince(role.name, since)

    override fun findById(id: String): AdminUser? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByUsername(username: String): AdminUser? =
        repository.findByUsername(username.trim().lowercase())?.toModel()

    override fun findBySlackMemberId(slackMemberId: String): AdminUser? =
        repository.findBySlackMemberId(slackMemberId.trim())?.toModel()

    override fun listByRole(role: AccountRole, approvalStatus: AccountApprovalStatus?): List<AdminUser> {
        val entities = if (approvalStatus != null) {
            repository.findByRoleAndApprovalStatus(role.name, approvalStatus.name)
        } else {
            repository.findByRole(role.name)
        }
        return entities.map { it.toModel() }.sortedByDescending { it.createdAt }
    }

    /**
     * 관리자 목록처럼 응답 크기가 정해진 조회에서 DB limit을 적용해 전체 사용자 로드를 피한다.
     */
    override fun listByRole(
        role: AccountRole,
        approvalStatus: AccountApprovalStatus?,
        limit: Int
    ): List<AdminUser> {
        val safeLimit = limit.coerceIn(1, MAX_LIST_BY_ROLE_LIMIT)
        val pageable = PageRequest.of(0, safeLimit)
        // 정렬과 제한을 DB에서 처리해 컨트롤러의 후속 activity/approver 조회 범위도 줄인다.
        val entities = if (approvalStatus != null) {
            repository.findByRoleAndApprovalStatusOrderByCreatedAtDesc(role.name, approvalStatus.name, pageable)
        } else {
            repository.findByRoleOrderByCreatedAtDesc(role.name, pageable)
        }
        return entities.map { it.toModel() }
    }

    override fun findPendingUsersCreatedBefore(cutoff: Instant): List<AdminUser> =
        repository.findByRoleAndApprovalStatusAndCreatedAtBefore(
            role = AccountRole.USER.name,
            approvalStatus = AccountApprovalStatus.PENDING.name,
            cutoff = cutoff
        ).map { it.toModel() }.sortedBy { it.createdAt }

    override fun findByIds(ids: List<String>): List<AdminUser> {
        if (ids.isEmpty()) return emptyList()
        return repository.findAllById(ids).map { it.toModel() }
    }

    override fun save(user: AdminUser): AdminUser {
        val now = Instant.now()
        val id = user.id.ifBlank { UUID.randomUUID().toString() }
        val entity = AdminUserEntity(
            id = id,
            username = user.username.trim().lowercase(),
            passwordHash = user.passwordHash,
            role = user.role.name,
            displayName = user.displayName,
            department = user.department,
            // V124(Phase 3 PR1): 부서 하위 team 필드
            team = user.team,
            // V129: FK 컬럼 (departments / teams 참조). 레거시 name 캐시는 위 2 필드 유지.
            departmentId = user.departmentId,
            teamId = user.teamId,
            isActive = user.isActive,
            approvalStatus = user.approvalStatus.name,
            approvalNote = user.approvalNote,
            approvedByUserId = user.approvedByUserId,
            approvedAt = user.approvedAt,
            slackMemberId = user.slackMemberId,
            slackDmChannelId = user.slackDmChannelId,
            mustChangePassword = user.mustChangePassword,
            lastLoginAt = user.lastLoginAt,
            createdAt = now,
            updatedAt = now
        )
        return repository.save(entity).toModel()
    }

    override fun update(user: AdminUser): AdminUser {
        val entity = repository.findById(user.id).orElseThrow {
            NotFoundException("Admin user not found: ${user.id}")
        }
        entity.displayName = user.displayName
        entity.department = user.department
        // V124(Phase 3 PR1): team 필드 업데이트 포함
        entity.team = user.team
        // V129: FK 컬럼도 업데이트 경로에 포함해 프로필 수정 시 반영한다.
        entity.departmentId = user.departmentId
        entity.teamId = user.teamId
        entity.isActive = user.isActive
        entity.approvalStatus = user.approvalStatus.name
        entity.approvalNote = user.approvalNote
        entity.approvedByUserId = user.approvedByUserId
        entity.approvedAt = user.approvedAt
        entity.slackMemberId = user.slackMemberId
        entity.slackDmChannelId = user.slackDmChannelId
        entity.mustChangePassword = user.mustChangePassword
        entity.lastLoginAt = user.lastLoginAt
        entity.updatedAt = Instant.now()
        return repository.save(entity).toModel()
    }

    @Transactional
    override fun updateLastLoginAt(username: String) {
        repository.updateLastLoginAt(username.trim().lowercase(), Instant.now())
    }

    @Transactional
    override fun anonymizeDeactivatedBefore(cutoff: Instant): Int =
        repository.anonymizeDeactivatedBefore(cutoff, Instant.now())

    @Transactional
    override fun updatePasswordHash(userId: String, passwordHash: String) {
        // 임시 비밀번호 초기화 시 해시만 교체하고 나머지 필드는 유지한다.
        repository.updatePasswordHash(userId, passwordHash, Instant.now())
    }

    @Transactional
    override fun updatePasswordHashAndFlags(
        userId: String,
        passwordHash: String,
        mustChangePassword: Boolean
    ) {
        // 비밀번호 해시와 mustChangePassword 플래그를 원자적으로 갱신한다.
        repository.updatePasswordHashAndFlags(userId, passwordHash, mustChangePassword, Instant.now())
    }

    private fun AdminUserEntity.toModel() = AdminUser(
        id = id,
        username = username,
        passwordHash = passwordHash,
        role = AccountRole.valueOf(role),
        displayName = displayName,
        department = department,
        team = team,
        departmentId = departmentId,
        teamId = teamId,
        isActive = isActive,
        approvalStatus = AccountApprovalStatus.valueOf(approvalStatus),
        approvalNote = approvalNote,
        approvedByUserId = approvedByUserId,
        approvedAt = approvedAt,
        slackMemberId = slackMemberId,
        slackDmChannelId = slackDmChannelId,
        mustChangePassword = mustChangePassword,
        lastLoginAt = lastLoginAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
