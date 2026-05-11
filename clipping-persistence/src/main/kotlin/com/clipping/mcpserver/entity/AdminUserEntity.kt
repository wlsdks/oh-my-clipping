package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 관리자/사용자 계정 엔티티.
 * admin_users 테이블에 매핑된다.
 */
@Entity
@Table(name = "admin_users")
class AdminUserEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 40, nullable = false, unique = true)
    var username: String = "",

    @Column(name = "password_hash", length = 255, nullable = false)
    var passwordHash: String = "",

    @Column(length = 20, nullable = false)
    var role: String = "ADMIN",

    @Column(name = "display_name", length = 100)
    var displayName: String? = null,

    @Column(length = 100)
    var department: String? = null,

    /**
     * 부서 하위 조직(팀). V124(Phase 3 PR1)에서 추가됐다.
     * 저장 전 [com.clipping.mcpserver.util.DepartmentNormalizer]로 정규화된다.
     */
    @Column(length = 64)
    var team: String? = null,

    /**
     * 부서 FK. V129 에서 추가됐다. departments(id) 를 참조하며 ON DELETE SET NULL.
     * 레거시 [department] 문자열은 이름 캐시로 6개월 유지한다.
     */
    @Column(name = "department_id", length = 36)
    var departmentId: String? = null,

    /**
     * 팀 FK. V129 에서 추가됐다. teams(id) 를 참조하며 ON DELETE SET NULL.
     * 레거시 [team] 문자열은 이름 캐시로 6개월 유지한다.
     */
    @Column(name = "team_id", length = 36)
    var teamId: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "approval_status", length = 20, nullable = false)
    var approvalStatus: String = "APPROVED",

    @Column(name = "approval_note", columnDefinition = "TEXT")
    var approvalNote: String? = null,

    @Column(name = "approved_by_user_id", length = 36)
    var approvedByUserId: String? = null,

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "slack_member_id", length = 20)
    var slackMemberId: String? = null,

    @Column(name = "slack_dm_channel_id", length = 80)
    var slackDmChannelId: String? = null,

    @Column(name = "must_change_password", nullable = false)
    var mustChangePassword: Boolean = false,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
