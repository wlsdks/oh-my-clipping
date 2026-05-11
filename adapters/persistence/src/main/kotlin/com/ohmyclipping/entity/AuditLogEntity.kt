package com.ohmyclipping.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 관리자 행동 감사 로그 엔티티.
 * audit_log 테이블에 매핑된다.
 * PK는 BIGSERIAL(자동 증가)이다.
 */
@Entity
@Table(name = "audit_log")
class AuditLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // V117 migration 이후 actor_id 는 admin_users(id) 로 FK 가 걸리고 nullable 로 바뀌었다.
    // 실존하지 않는 principal(admin-api 등)이나 탈퇴 계정의 과거 로그는 NULL 로 끊긴다.
    @Column(name = "actor_id", length = 36, nullable = true)
    val actorId: String? = null,

    @Column(name = "actor_name", length = 100, nullable = false)
    val actorName: String = "",

    @Column(length = 50, nullable = false)
    val action: String = "",

    @Column(name = "target_type", length = 50, nullable = false)
    val targetType: String = "",

    @Column(name = "target_id", length = 100)
    val targetId: String? = null,

    @Column(name = "target_name", length = 200)
    val targetName: String? = null,

    @Column(columnDefinition = "TEXT")
    val detail: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
