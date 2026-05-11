package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.AdminUserEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface AdminUserRepository : JpaRepository<AdminUserEntity, String> {
    fun findByUsername(username: String): AdminUserEntity?
    fun findBySlackMemberId(slackMemberId: String): AdminUserEntity?
    fun findByRole(role: String): List<AdminUserEntity>
    fun findByRoleAndApprovalStatus(role: String, approvalStatus: String): List<AdminUserEntity>
    fun findByRoleOrderByCreatedAtDesc(role: String, pageable: Pageable): List<AdminUserEntity>
    fun findByRoleAndApprovalStatusOrderByCreatedAtDesc(
        role: String,
        approvalStatus: String,
        pageable: Pageable,
    ): List<AdminUserEntity>
    fun countByRole(role: String): Int
    fun countByRoleAndApprovalStatus(role: String, approvalStatus: String): Int
    fun countByDepartmentId(departmentId: String): Int
    fun countByTeamId(teamId: String): Int

    /**
     * 지정 역할과 승인 상태를 가지면서, createdAt 이 cutoff 이전인 계정 목록을 조회한다.
     * SLA 에스컬레이션 스케줄러에서 `role='USER'`, `approvalStatus='PENDING'` 조합으로 사용한다.
     */
    fun findByRoleAndApprovalStatusAndCreatedAtBefore(
        role: String,
        approvalStatus: String,
        cutoff: java.time.Instant,
    ): List<AdminUserEntity>

    @Query(
        "SELECT COUNT(u) FROM AdminUserEntity u WHERE u.role = :role AND u.approvedAt >= :since AND u.approvalStatus IN ('APPROVED', 'REJECTED')"
    )
    fun countProcessedSince(role: String, since: java.time.Instant): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AdminUserEntity u SET u.lastLoginAt = :now WHERE u.username = :username")
    fun updateLastLoginAt(username: String, now: java.time.Instant): Int

    @Modifying
    @Query(
        """UPDATE AdminUserEntity u SET u.username = CONCAT('withdrawn_', SUBSTRING(u.id, 1, 8)),
           u.displayName = null, u.department = null, u.team = null,
           u.slackMemberId = null, u.slackDmChannelId = null,
           u.passwordHash = 'ANONYMIZED', u.updatedAt = :now
           WHERE u.isActive = false AND u.approvalStatus = 'REJECTED' AND u.updatedAt < :cutoff"""
    )
    fun anonymizeDeactivatedBefore(cutoff: java.time.Instant, now: java.time.Instant): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE AdminUserEntity u SET u.passwordHash = :passwordHash, u.updatedAt = :now WHERE u.id = :id")
    fun updatePasswordHash(id: String, passwordHash: String, now: java.time.Instant): Int

    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE AdminUserEntity u SET u.passwordHash = :passwordHash, " +
            "u.mustChangePassword = :mustChangePassword, u.updatedAt = :now WHERE u.id = :id"
    )
    fun updatePasswordHashAndFlags(
        id: String,
        passwordHash: String,
        mustChangePassword: Boolean,
        now: java.time.Instant
    ): Int
}
